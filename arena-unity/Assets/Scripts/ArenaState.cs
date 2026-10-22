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

    [Serializable]
    public class QuizQuestion
    {
        public string id;
        public string prompt;
        public List<string> choices = new List<string>();
        public int answer;
        public string why;
        public string skill;
        public string forKid;
    }

    [Serializable]
    public class QuizAnswer
    {
        public string questionId;
        public string kidId;
        public int choice;
        public bool correct;
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
        /// <summary>"quiz" = the Stone Trial (family Quiz Battle); "raid" = the retired Mess Boss fight.</summary>
        public string mode = "quiz";
        public List<QuizQuestion> questions = new List<QuizQuestion>();
        public List<QuizAnswer> answers = new List<QuizAnswer>();
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
            if (Raid.mode == "raid" && !string.IsNullOrEmpty(Raid.apiUrl)) StartCoroutine(PollLive());
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

        /// <summary>Launched on its own (no TV intent): a sample Stone Trial from the demo family's homework.</summary>
        static RaidInfo Fallback()
        {
            var r = new RaidInfo();
            r.kids.Add(new KidInfo { id = "arjun", name = "Arjun", avatar = "avatar_arjun" });
            r.kids.Add(new KidInfo { id = "meera", name = "Meera", avatar = "avatar_meera_crown" });
            void Q(string id, string kid, string prompt, int answer, string why, string skill, params string[] choices) =>
                r.questions.Add(new QuizQuestion { id = id, forKid = kid, prompt = prompt, answer = answer, why = why, skill = skill, choices = new List<string>(choices) });
            Q("z1", "meera", "What is 5 - 2?", 3, "Count back 2 from 5.", "Subtraction", "2", "4", "1", "3");
            Q("z2", "arjun", "Simplify 4/8.", 2, "Divide the top and bottom by 4.", "Simplifying fractions", "2/4", "2/8", "1/2", "1/4");
            Q("z3", "meera", "What is 7 - 3?", 1, "Count back 3 from 7.", "Subtraction", "5", "4", "3", "2");
            Q("z4", "arjun", "What is 1/4 + 2/4?", 0, "Add the tops, keep the bottom.", "Adding fractions", "3/4", "3/8", "1/2", "2/4");
            Q("z5", "meera", "What is 10 - 5?", 1, "Half of 10 is 5.", "Subtraction", "4", "5", "7", "6");
            Q("z6", "arjun", "Simplify 6/12.", 2, "Divide the top and bottom by 6.", "Simplifying fractions", "1/3", "3/6", "1/2", "2/4");
            return r;
        }
    }
}
