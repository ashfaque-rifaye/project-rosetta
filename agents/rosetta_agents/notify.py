"""Family feed + parent alerts.

Every important moment becomes one feed event (shown on the TV, kid phones and parent phones) and is fanned out to
each parent on the channels they switched on:

  push  - Web Push to the installed parent app (VAPID, free, Android + iOS 16.4+ home-screen apps)
  email - Amazon SES (sender must be a verified identity)
  sms   - Amazon SNS (in some countries, e.g. India, transactional SMS needs sender registration)

Channel failures never break the quest flow: they're logged and recorded on the event.
"""
from __future__ import annotations

import json
import logging
import os
import time

import boto3

from . import config

log = logging.getLogger(__name__)

VAPID_PUBLIC = os.environ.get("ROSETTA_VAPID_PUBLIC", "")
VAPID_PRIVATE = os.environ.get("ROSETTA_VAPID_PRIVATE", "")
VAPID_CONTACT = os.environ.get("ROSETTA_VAPID_CONTACT", "mailto:alerts@example.com")
SES_FROM = os.environ.get("ROSETTA_SES_FROM", "")
WEB_URL = os.environ.get("ROSETTA_WEB_URL", "")

# kind -> (who gets alerted, urgent?)  Family-feed-only kinds are not in this map.
ALERTS = {
    "quest_done": ("parents", False),
    "needs_approval": ("parents", True),
    "done_check": ("parents", True),
    "habit_done": ("parents", False),
    "wish_request": ("parents", True),
    "wish_ready": ("parents", False),
    "tv_request": ("parents", True),
    "quiz_done": ("parents", False),
    "goal_done": ("parents", False),
    "streak_risk": ("parents", False),
    "weekly_digest": ("parents", False),
}

_ses = boto3.client("ses", region_name=config.REGION)
_sns = boto3.client("sns", region_name=config.REGION)


def emit(h: dict, kind: str, title: str, body: str, *, by: str = "rosetta", data: dict | None = None, notify: bool = True) -> dict:
    """Append a feed event to household [h] (caller saves) and alert parents if this kind is alert-worthy."""
    ev = {
        "id": f"f{int(time.time() * 1000)}{len(h.get('feed', []))}",
        "kind": kind, "title": title[:80], "body": body[:280], "by": by,
        "atMs": int(time.time() * 1000), "data": {**(data or {}), "code": h.get("code", "")}, "delivered": [],
    }
    h["feed"] = (h.get("feed", []) + [ev])[-60:]
    if notify and kind in ALERTS:
        for parent in h.get("parents", []):
            ev["delivered"] += _fan_out(parent, ev)
    return ev


def _fan_out(parent: dict, ev: dict) -> list[str]:
    prefs = parent.get("alerts", {"push": True, "email": False, "sms": False})
    done = []
    if prefs.get("push"):
        for sub in list(parent.get("push", [])):
            if _push(sub, ev):
                done.append(f"push:{parent['id']}")
    if prefs.get("email") and parent.get("email"):
        if _email(parent["email"], ev):
            done.append(f"email:{parent['id']}")
    urgent = ALERTS.get(ev["kind"], ("", False))[1]
    if prefs.get("sms") and parent.get("phone") and urgent:  # SMS only for things that need a parent now
        if _sms(parent["phone"], ev):
            done.append(f"sms:{parent['id']}")
    return done


def _push(subscription: dict, ev: dict) -> bool:
    if not (VAPID_PUBLIC and VAPID_PRIVATE):
        return False
    try:
        from pywebpush import webpush  # bundled in the Lambda zip
        payload = {
            "title": ev["title"], "body": ev["body"], "kind": ev["kind"], "data": ev["data"],
            "actions": [{"action": "approve", "title": "Approve ✓"}, {"action": "reject", "title": "Not yet"}] if ev["kind"] == "needs_approval" else [],
        }
        webpush(subscription_info=subscription, data=json.dumps(payload), vapid_private_key=VAPID_PRIVATE,
                vapid_claims={"sub": VAPID_CONTACT}, ttl=3600)
        return True
    except Exception as e:  # noqa: BLE001 - a dead subscription must not break the flow
        log.warning("push failed: %s", e)
        return False


def _email(to: str, ev: dict) -> bool:
    if not SES_FROM:
        return False
    try:
        link = f"{WEB_URL}/parent.html" if WEB_URL else ""
        _ses.send_email(
            Source=SES_FROM, Destination={"ToAddresses": [to]},
            Message={
                "Subject": {"Data": f"Rosetta · {ev['title']}"},
                "Body": {"Text": {"Data": f"{ev['body']}\n\n{('Open Rosetta for parents: ' + link) if link else ''}"}},
            },
        )
        return True
    except Exception as e:  # noqa: BLE001
        log.warning("email failed: %s", e)
        return False


def _sms(phone: str, ev: dict) -> bool:
    try:
        _sns.publish(PhoneNumber=phone, Message=f"Rosetta: {ev['title']}. {ev['body']}"[:300],
                     MessageAttributes={"AWS.SNS.SMS.SMSType": {"DataType": "String", "StringValue": "Transactional"}})
        return True
    except Exception as e:  # noqa: BLE001
        log.warning("sms failed: %s", e)
        return False
