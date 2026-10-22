using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;

namespace Rosetta.Arena
{
    /// <summary>
    /// Screen-space HUD sized for a 10-foot TV view: boss name plate, HP bar with a trailing "ghost" bar, combo badge,
    /// floating damage numbers, the team contribution bar with avatars, and the victory loot card.
    /// </summary>
    public class ArenaHud : MonoBehaviour
    {
        static readonly Color Gold = new Color(1f, 0.82f, 0.25f);
        static readonly Color Cream = new Color(1f, 0.96f, 0.88f);
        static readonly Color Night = new Color(0.06f, 0.09f, 0.19f, 0.86f);
        // Validated per-kid palette shared with the TV app (Rosetta.KidSeries).
        static readonly Color[] KidColors = { Hex("749A10"), Hex("3D97D1"), Hex("E0477F"), Hex("A257DD") };

        ArenaState state;
        RectTransform canvas, hpFill, hpGhost, team;
        Text hpText, comboText, toast;
        CanvasGroup comboGroup;
        Font font;
        float ghost = 1f, comboPop, comboAt = -99f;
        readonly List<(string id, RectTransform seg, Text label)> segs = new List<(string, RectTransform, Text)>();

        static Color Hex(string h) { ColorUtility.TryParseHtmlString("#" + h, out var c); return c; }

        public void Init(ArenaState s)
        {
            state = s;
            font = Resources.Load<Font>("Fonts/Fredoka") ?? Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
            var go = new GameObject("Hud", typeof(Canvas), typeof(CanvasScaler));
            var c = go.GetComponent<Canvas>();
            c.renderMode = RenderMode.ScreenSpaceOverlay;
            c.sortingOrder = 10;
            var scaler = go.GetComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1920, 1080);
            scaler.matchWidthOrHeight = 1f;
            canvas = (RectTransform)go.transform;
            BuildTop();
            BuildTeam();
            BuildCombo();
            toast = Label(canvas, "Every Lost Thing you put away hits the boss", 30, Cream, TextAnchor.MiddleCenter);
            Place(toast.rectTransform, new Vector2(0.5f, 0), new Vector2(0, 205), new Vector2(1400, 50));
            var hint = Label(canvas, "BACK  return to Home Base", 22, new Color(1, 1, 1, 0.55f), TextAnchor.MiddleRight);
            Place(hint.rectTransform, new Vector2(1, 1), new Vector2(-60, -48), new Vector2(520, 36), new Vector2(1, 0.5f));
            Refresh(true);
        }

        // ------------------------------------------------------------------ building blocks

        Image Panel(RectTransform parent, Color color)
        {
            var go = new GameObject("Panel", typeof(RectTransform), typeof(Image));
            go.transform.SetParent(parent, false);
            var img = go.GetComponent<Image>();
            img.color = color;
            return img;
        }

        Text Label(RectTransform parent, string text, int size, Color color, TextAnchor anchor, bool bold = true)
        {
            var go = new GameObject("Text", typeof(RectTransform), typeof(Text), typeof(Outline), typeof(Shadow));
            go.transform.SetParent(parent, false);
            var t = go.GetComponent<Text>();
            t.font = font;
            t.text = text;
            t.fontSize = size;
            t.fontStyle = bold ? FontStyle.Bold : FontStyle.Normal;
            t.color = color;
            t.alignment = anchor;
            t.horizontalOverflow = HorizontalWrapMode.Overflow;
            t.verticalOverflow = VerticalWrapMode.Overflow;
            t.raycastTarget = false;
            go.GetComponent<Outline>().effectColor = new Color(0.03f, 0.04f, 0.1f, 0.85f);
            go.GetComponent<Outline>().effectDistance = new Vector2(2, -2);
            go.GetComponent<Shadow>().effectColor = new Color(0, 0, 0, 0.5f);
            go.GetComponent<Shadow>().effectDistance = new Vector2(0, -5);
            return t;
        }

        static void Place(RectTransform r, Vector2 anchor, Vector2 pos, Vector2 size, Vector2? pivot = null)
        {
            r.anchorMin = r.anchorMax = anchor;
            r.pivot = pivot ?? new Vector2(0.5f, 0.5f);
            r.anchoredPosition = pos;
            r.sizeDelta = size;
        }

        static void Fill(RectTransform r, float from, float to)
        {
            r.anchorMin = new Vector2(from, 0);
            r.anchorMax = new Vector2(to, 1);
            r.offsetMin = r.offsetMax = Vector2.zero;
        }

        void BuildTop()
        {
            var raid = state.Raid;
            var tag = Label(canvas, "MESS BOSS RAID  ·  " + raid.room.ToUpperInvariant(), 24, Gold, TextAnchor.MiddleCenter);
            Place(tag.rectTransform, new Vector2(0.5f, 1), new Vector2(0, -52), new Vector2(900, 34));
            var name = Label(canvas, raid.bossName, 64, Cream, TextAnchor.MiddleCenter);
            Place(name.rectTransform, new Vector2(0.5f, 1), new Vector2(0, -104), new Vector2(1200, 74));
            var line = Label(canvas, raid.tagline, 26, new Color(1, 1, 1, 0.75f), TextAnchor.MiddleCenter, false);
            Place(line.rectTransform, new Vector2(0.5f, 1), new Vector2(0, -150), new Vector2(1200, 34));

            var frame = Panel(canvas, Night).rectTransform;
            Place(frame, new Vector2(0.5f, 1), new Vector2(0, -196), new Vector2(1040, 40));
            var track = Panel(frame, new Color(1, 1, 1, 0.08f)).rectTransform;
            track.anchorMin = Vector2.zero; track.anchorMax = Vector2.one;
            track.offsetMin = new Vector2(6, 6); track.offsetMax = new Vector2(-6, -6);
            hpGhost = Panel(track, new Color(1f, 0.95f, 0.85f, 0.85f)).rectTransform;
            hpFill = Panel(track, Hex("E0477F")).rectTransform;
            var sheen = Panel(hpFill, new Color(1, 1, 1, 0.22f)).rectTransform;
            sheen.anchorMin = new Vector2(0, 0.55f); sheen.anchorMax = Vector2.one;
            sheen.offsetMin = sheen.offsetMax = Vector2.zero;
            hpText = Label(frame, "", 24, Cream, TextAnchor.MiddleCenter);
            hpText.rectTransform.anchorMin = Vector2.zero; hpText.rectTransform.anchorMax = Vector2.one;
            hpText.rectTransform.offsetMin = hpText.rectTransform.offsetMax = Vector2.zero;
        }

        void BuildTeam()
        {
            var panel = Panel(canvas, Night).rectTransform;
            Place(panel, new Vector2(0.5f, 0), new Vector2(0, 110), new Vector2(1200, 108));
            var title = Label(panel, "TEAM DAMAGE", 20, Gold, TextAnchor.MiddleLeft);
            Place(title.rectTransform, new Vector2(0, 1), new Vector2(24, -22), new Vector2(300, 28), new Vector2(0, 0.5f));
            team = Panel(panel, new Color(1, 1, 1, 0.07f)).rectTransform;
            team.anchorMin = new Vector2(0, 0); team.anchorMax = new Vector2(1, 0);
            team.pivot = new Vector2(0.5f, 0);
            team.offsetMin = new Vector2(24, 18); team.offsetMax = new Vector2(-24, 58);
            for (int i = 0; i < state.Raid.kids.Count; i++)
            {
                var kid = state.Raid.kids[i];
                var seg = Panel(team, KidColors[i % KidColors.Length]).rectTransform;
                var label = Label(seg, "", 22, Color.white, TextAnchor.MiddleLeft);
                label.rectTransform.anchorMin = Vector2.zero; label.rectTransform.anchorMax = Vector2.one;
                label.rectTransform.offsetMin = new Vector2(52, 0); label.rectTransform.offsetMax = Vector2.zero;
                var tex = ArenaAssets.Tex(kid.avatar ?? "avatar_" + kid.id);
                if (tex != null)
                {
                    var av = new GameObject("Avatar", typeof(RectTransform), typeof(RawImage));
                    av.transform.SetParent(seg, false);
                    av.GetComponent<RawImage>().texture = tex;
                    Place((RectTransform)av.transform, new Vector2(0, 0.5f), new Vector2(6, 0), new Vector2(40, 40), new Vector2(0, 0.5f));
                }
                segs.Add((kid.id, seg, label));
            }
        }

        void BuildCombo()
        {
            var go = new GameObject("Combo", typeof(RectTransform), typeof(CanvasGroup));
            go.transform.SetParent(canvas, false);
            var r = (RectTransform)go.transform;
            Place(r, new Vector2(1, 0.5f), new Vector2(-200, 80), new Vector2(320, 140));
            comboGroup = go.GetComponent<CanvasGroup>();
            comboGroup.alpha = 0;
            comboText = Label(r, "", 76, Gold, TextAnchor.MiddleCenter);
            comboText.supportRichText = true;
            comboText.rectTransform.anchorMin = Vector2.zero; comboText.rectTransform.anchorMax = Vector2.one;
            comboText.rectTransform.offsetMin = comboText.rectTransform.offsetMax = Vector2.zero;
        }

        // ------------------------------------------------------------------ live updates

        string NameOf(string id)
        {
            foreach (var k in state.Raid.kids) if (k.id == id) return k.name;
            return id;
        }

        void Refresh(bool snapGhost = false)
        {
            float frac = (float)state.Hp / state.Raid.maxHp;
            Fill(hpFill, 0, frac);
            if (snapGhost) ghost = frac;
            hpText.text = $"{state.Hp:N0} / {state.Raid.maxHp:N0} HP";
            int total = Mathf.Max(1, state.Dealt);
            float x = 0;
            foreach (var (id, seg, label) in segs)
            {
                float w = state.Dealt == 0 ? 1f / segs.Count : (float)state.DealtBy(id) / total;
                Fill(seg, x, x + w);
                x += w;
                label.text = w > 0.12f ? $"{NameOf(id)}  {state.DealtBy(id):N0}" : "";
            }
        }

        public void ShowDamage(HitInfo hit, Vector3 screen)
        {
            Refresh();
            string who = NameOf(hit.kidId);
            var t = Label(canvas, (hit.crit ? "CRIT! " : "") + "-" + hit.damage, hit.crit ? 92 : 68, hit.crit ? Gold : Cream, TextAnchor.MiddleCenter);
            var r = t.rectTransform;
            r.anchorMin = r.anchorMax = Vector2.zero;
            var scale = canvas.rect.height / Screen.height;
            r.anchoredPosition = new Vector2(screen.x * scale, screen.y * scale);
            r.sizeDelta = new Vector2(500, 110);
            StartCoroutine(Float(t));
            toast.text = $"{who} put away the {hit.thing}!  −{hit.damage} HP";
            if (state.Combo >= 2)
            {
                comboText.text = $"x{state.Combo}\n<size=30>COMBO</size>";
                                comboGroup.alpha = 1;
                comboPop = 1f;
                comboAt = Time.unscaledTime;
            }
        }

        IEnumerator Float(Text t)
        {
            var r = t.rectTransform;
            var start = r.anchoredPosition;
            float time = 0;
            while (time < 1.3f)
            {
                time += Time.unscaledDeltaTime;
                float k = time / 1.3f;
                float pop = k < 0.12f ? Mathf.Lerp(0.4f, 1.25f, k / 0.12f) : Mathf.Lerp(1.25f, 1f, (k - 0.12f) * 3f);
                r.localScale = Vector3.one * Mathf.Max(1f, pop);
                r.anchoredPosition = start + new Vector2(0, 160 * Mathf.Sqrt(k));
                var c = t.color; c.a = 1 - Mathf.Clamp01((k - 0.6f) / 0.4f); t.color = c;
                yield return null;
            }
            Destroy(t.gameObject);
        }

        public void ShowVictory()
        {
            Refresh();
            // Gold frame behind a fully opaque night panel: in linear colour space a translucent panel over an Outline
            // blends the gold through and the card turns muddy brown.
            var frame = Panel(canvas, Gold).rectTransform;
            Place(frame, new Vector2(0.5f, 0.5f), new Vector2(0, 40), new Vector2(988, 468));
            var card = Panel(frame, new Color(0.06f, 0.09f, 0.19f, 1f)).rectTransform;
            card.anchorMin = Vector2.zero; card.anchorMax = Vector2.one;
            card.offsetMin = new Vector2(4, 4); card.offsetMax = new Vector2(-4, -4);
            var title = Label(card, "BOSS DEFEATED!", 84, Gold, TextAnchor.MiddleCenter);
            Place(title.rectTransform, new Vector2(0.5f, 1), new Vector2(0, -80), new Vector2(940, 100));
            var sub = Label(card, $"{state.Raid.bossName} is back in the toy box.", 30, Cream, TextAnchor.MiddleCenter, false);
            Place(sub.rectTransform, new Vector2(0.5f, 1), new Vector2(0, -150), new Vector2(940, 40));
            var loot = Label(card, "Everyone gets  +200 coins   +150 XP", 44, Color.white, TextAnchor.MiddleCenter);
            Place(loot.rectTransform, new Vector2(0.5f, 0.5f), new Vector2(0, -10), new Vector2(940, 60));
            // Co-op, never a ranking: celebrate the team's total, not a top hitter.
            var teamLine = Label(card, $"{state.Raid.hits.Count} Lost Things home · {state.Dealt:N0} team damage. Teamwork wins.", 28,
                new Color(1, 1, 1, 0.8f), TextAnchor.MiddleCenter, false);
            Place(teamLine.rectTransform, new Vector2(0.5f, 0.5f), new Vector2(0, -80), new Vector2(940, 40));
            var ok = Label(card, "Press OK to collect", 30, Gold, TextAnchor.MiddleCenter);
            Place(ok.rectTransform, new Vector2(0.5f, 0), new Vector2(0, 60), new Vector2(600, 40));
            toast.text = "";
            StartCoroutine(Pop(frame));
        }

        IEnumerator Pop(RectTransform r)
        {
            float t = 0;
            while (t < 1f)
            {
                t += Time.unscaledDeltaTime * 2.5f;
                float k = Mathf.Clamp01(t);
                float s = 1f + Mathf.Sin(k * Mathf.PI) * 0.12f;
                r.localScale = Vector3.one * Mathf.Lerp(0.3f, 1f, 1 - Mathf.Pow(1 - k, 3)) * s;
                yield return null;
            }
            r.localScale = Vector3.one;
        }

        void Update()
        {
            if (state == null) return;
            float frac = (float)state.Hp / state.Raid.maxHp;
            ghost = Mathf.MoveTowards(ghost, frac, Time.unscaledDeltaTime * 0.35f);
            Fill(hpGhost, 0, Mathf.Max(ghost, frac));
            if (comboPop > 0)
            {
                comboPop = Mathf.MoveTowards(comboPop, 0, Time.unscaledDeltaTime * 4f);
                comboText.rectTransform.localScale = Vector3.one * (1f + comboPop * 0.35f);
            }
            if (Time.unscaledTime - comboAt > 4f) comboGroup.alpha = Mathf.MoveTowards(comboGroup.alpha, 0, Time.unscaledDeltaTime * 2f);
        }
    }
}
