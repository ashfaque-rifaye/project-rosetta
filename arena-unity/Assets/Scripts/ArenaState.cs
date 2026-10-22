using System;
using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Networking;

namespace Rosetta.Arena
{
    [Serializable] public class KidInfo { public string id; public string name; public string avatar; }

    [Serializable]
    public class HitInfo
    {
        public string kidId;
        public string thing;
        public int damage;
        public bool crit;
    }

    /// <summary>What the TV app hands the arena (Intent extra "raid", JSON) or what the Rosetta API returns.</summary>
    [Serializable]
    public class RaidInfo
    {
        public string bossId = "kraken";
        public string bossName = "The Sock Kraken";
        public string tagline = "Eight arms. Zero matching socks.";
        public string room = "Playroom";
        public int maxHp = 1200;
        public List<KidInfo> kids = new List<KidInfo>();
        public List<HitInfo> hits = new List<HitInfo>();
        /// <summary>When set, the arena polls the live household for new verified put-aways.</summary>
        public string apiUrl = "";
        public string code = "R4KX";
    }

    /// <summary>
    /// Single source of truth for the fight. New hits arrive from the agent crew (live API), from the TV app,
    /// or, in development, from the remote's MENU key standing in for a verified put-away.
    /// </summary>
    public class ArenaState : MonoBehaviour
    {
        public RaidInfo Raid { get; private set; }
        public int Dealt { get; private set; }
        public int Hp => Mathf.Max(0, Raid.maxHp - Dealt);
        public bool Defeated => Hp == 0;
        public int Combo { get; private set; }
        public event Action<HitInfo> OnHit;
        public event Action OnDefeated;

        float lastHitTime = -999f;
        int devHits;
        readonly HashSet<int> seen = new HashSet<int>();

        static readonly string[] Things = { "sock", "teddy", "bricks", "sneakers", "books", "dino", "jacket", "mug" };

        void Awake()
        {
            Raid = ReadIntent() ?? Fallback();
            foreach (var h in Raid.hits) Dealt += h.damage;
        }

        void Start()
        {
            if (!string.IsNullOrEmpty(Raid.apiUrl)) StartCoroutine(PollLive());
        }

        public int DealtBy(string kidId)
        {
            int sum = 0;
            foreach (var h in Raid.hits) if (h.kidId == kidId) sum += h.damage;
            return sum;
        }

        public void ApplyHit(HitInfo hit)
        {
            if (Defeated) return;
            Combo = Time.unscaledTime - lastHitTime < 20f ? Combo + 1 : 1;
            lastHitTime = Time.unscaledTime;
            Raid.hits.Add(hit);
            Dealt += hit.damage;
            OnHit?.Invoke(hit);
            if (Defeated) OnDefeated?.Invoke();
        }

        /// <summary>Development stand-in for one AI-verified put-away (remote MENU key).</summary>
        public void DevHit()
        {
            var kid = Raid.kids.Count > 0 ? Raid.kids[devHits % Raid.kids.Count] : new KidInfo { id = "arjun" };
            bool crit = devHits % 4 == 3;
            int baseDmg = 60 + (devHits * 17) % 40;
            int dmg = Mathf.RoundToInt((crit ? baseDmg * 2 : baseDmg) * (1f + 0.1f * Mathf.Min(Combo, 5)));
            devHits++;
            ApplyHit(new HitInfo { kidId = kid.id, thing = Things[devHits % Things.Length], damage = dmg, crit = crit });
        }

        IEnumerator PollLive()
        {
            var url = Raid.apiUrl.TrimEnd('/') + "/household/" + Raid.code + "/raid";
            while (!Defeated)
            {
                using (var req = UnityWebRequest.Get(url))
                {
                    yield return req.SendWebRequest();
                    if (req.result == UnityWebRequest.Result.Success)
                    {
                        var live = JsonUtility.FromJson<RaidInfo>(req.downloadHandler.text);
                        for (int i = 0; i < live.hits.Count; i++)
                            if (i >= Raid.hits.Count && seen.Add(i)) ApplyHit(live.hits[i]);
                    }
                }
                yield return new WaitForSecondsRealtime(1f);
            }
        }

        /// <summary>The TV app launches the arena with the raid as JSON in the "raid" extra.</summary>
        static RaidInfo ReadIntent()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using var player = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
                using var activity = player.GetStatic<AndroidJavaObject>("currentActivity");
                using var intent = activity.Call<AndroidJavaObject>("getIntent");
                var json = intent.Call<string>("getStringExtra", "raid");
                if (!string.IsNullOrEmpty(json)) return JsonUtility.FromJson<RaidInfo>(json);
            }
            catch (Exception e) { Debug.LogWarning("Rosetta: no raid intent: " + e.Message); }
#endif
            return null;
        }

        /// <summary>Hands the result back to the TV app and closes the arena.</summary>
        public void Finish()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using var player = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
                using var activity = player.GetStatic<AndroidJavaObject>("currentActivity");
                using var data = new AndroidJavaObject("android.content.Intent");
                data.Call<AndroidJavaObject>("putExtra", "raid", JsonUtility.ToJson(Raid));
                activity.Call("setResult", -1, data);
                activity.Call("finish");
                return;
            }
            catch (Exception e) { Debug.LogWarning("Rosetta: finish failed: " + e.Message); }
#endif
            Application.Quit();
        }

        static RaidInfo Fallback()
        {
            var r = new RaidInfo();
            r.kids.Add(new KidInfo { id = "arjun", name = "Arjun", avatar = "avatar_arjun" });
            r.kids.Add(new KidInfo { id = "meera", name = "Meera", avatar = "avatar_meera_crown" });
            return r;
        }
    }
}
