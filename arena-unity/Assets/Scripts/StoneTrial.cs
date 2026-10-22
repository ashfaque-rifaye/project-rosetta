using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;
using UnityEngine.UI;

namespace Rosetta.Arena
{
    /// <summary>
    /// The family Quiz Battle in 3D: the Family Stone fills the left of the screen with one carved socket per question.
    /// A right answer ignites its socket (glow, sparks, bloom, a little shake); a miss gives a soft crack of dust and the
    /// right answer with its method. Turns alternate between the kids. Co-op only: the team total, never a ranking.
    /// Questions come from the TV (the Quizmaster agent built them from this week's homework); answers go back to the TV.
    /// </summary>
    public class StoneTrial : MonoBehaviour
    {
        // Where the stone's carved face sits in stone.png (same rect the TV's Stone screen uses).
        static readonly Rect Face = Rect.MinMaxRect(0.378f, 0.163f, 0.625f, 0.844f);
        static readonly Color Gold = new Color(1f, 0.82f, 0.4f);
        static readonly Color Cream = new Color(1f, 0.96f, 0.88f);
        static readonly Color Lime = new Color(0.78f, 0.96f, 0.2f);
        static readonly Color Rose = new Color(1f, 0.44f, 0.64f);
        // Opaque UI colours: in linear colour space, translucent panels pick up whatever is behind them and turn muddy.
        static readonly Color Night = new Color(0.06f, 0.08f, 0.17f, 1f);
        static readonly Color ChoiceIdle = new Color(0.13f, 0.17f, 0.31f, 1f);
        static readonly Color ChoiceFocus = new Color(0.5f, 0.38f, 0.12f, 1f);
        static readonly Color ChoiceRight = new Color(0.36f, 0.5f, 0.06f, 1f);
        static readonly Color ChoiceWrong = new Color(0.55f, 0.16f, 0.3f, 1f);
        static readonly string[] Glyphs = { "bed", "books", "shirt", "plant", "broom", "dish", "package", "shoes", "scholar" };

        ArenaState state;
        Camera cam;
        Vector3 camBase;
        float shake;
        readonly List<Transform> sockets = new List<Transform>();
        readonly List<Material> socketMats = new List<Material>();
        readonly List<Material> glyphMats = new List<Material>();
        readonly List<float> socketLit = new List<float>();
        ParticleSystem sparks, dust, confetti;
        Bloom bloom;
        Font font;
        RectTransform canvas, choiceRoot;
        Text turnText, progressText, promptText, feedbackText, teamText;
        RawImage turnAvatar;
        readonly List<Image> choiceBgs = new List<Image>();
        readonly List<Text> choiceTexts = new List<Text>();
        int selected, current = -1;
        bool locked, finished;

        RaidInfo Raid => state.Raid;

        public void Init(ArenaState s)
        {
            state = s;
            font = Resources.Load<Font>("Fonts/Fredoka") ?? Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
            BuildCamera();
            BuildStone();
            BuildFx();
            BuildHud();
            for (int i = 0; i < Raid.questions.Count; i++)
                if (Raid.answers.Exists(a => a.questionId == Raid.questions[i].id && a.correct)) socketLit[i] = 1f;
            Next();
        }

        // ------------------------------------------------------------------ world

        void BuildCamera()
        {
            var go = new GameObject("Camera");
            cam = go.AddComponent<Camera>();
            cam.clearFlags = CameraClearFlags.SolidColor;
            cam.backgroundColor = new Color(0.03f, 0.04f, 0.09f);
            cam.fieldOfView = 34f;
            cam.transform.position = camBase = new Vector3(0, 0, -10f);
            var data = go.AddComponent<UniversalAdditionalCameraData>();
            data.renderPostProcessing = true;
            data.antialiasing = AntialiasingMode.FastApproximateAntialiasing;

            var volume = new GameObject("PostFx").AddComponent<Volume>();
            volume.isGlobal = true;
            var profile = ScriptableObject.CreateInstance<VolumeProfile>();
            bloom = profile.Add<Bloom>(true);
            bloom.intensity.Override(1.1f);
            bloom.threshold.Override(0.8f);
            bloom.scatter.Override(0.75f);
            var vignette = profile.Add<Vignette>(true);
            vignette.intensity.Override(0.42f);
            vignette.smoothness.Override(0.5f);
            var color = profile.Add<ColorAdjustments>(true);
            color.saturation.Override(8f);
            color.contrast.Override(10f);
            volume.profile = profile;
        }

        Transform Quad(string name, Texture2D tex, Vector3 pos, Vector2 size, bool transparent, bool additive = false)
        {
            var q = GameObject.CreatePrimitive(PrimitiveType.Quad);
            q.name = name;
            Destroy(q.GetComponent<Collider>());
            q.transform.position = pos;
            q.transform.localScale = new Vector3(size.x, size.y, 1);
            var m = new Material(ArenaAssets.Shader(transparent ? "transparent" : "opaque"));
            m.mainTexture = tex;
            m.SetTexture("_BaseMap", tex);
            if (transparent) ArenaAssets.MakeTransparent(m, additive);
            q.GetComponent<MeshRenderer>().sharedMaterial = m;
            return q.transform;
        }

        void BuildStone()
        {
            var tex = ArenaAssets.Tex("stone");
            float aspect = tex ? (float)tex.width / tex.height : 16f / 9f;
            // Height that fills the frame at z=0, a little extra for camera drift.
            float h = 2f * 10f * Mathf.Tan(cam.fieldOfView * 0.5f * Mathf.Deg2Rad) * 1.08f;
            float w = h * aspect;
            // Slide the art so the stone's face sits at ~30% of the screen width, leaving the right for the question.
            float viewW = h / 1.08f * cam.aspect;
            float faceCx = (Face.xMin + Face.xMax) / 2f;
            float x = -viewW * 0.2f - (faceCx - 0.5f) * w;
            var stone = Quad("Stone", tex, new Vector3(x, 0, 0), new Vector2(w, h), false);

            // Sockets: a 3-column grid on the carved face (image y runs down, world y runs up).
            int n = Mathf.Max(1, Raid.questions.Count);
            int cols = 3, rows = Mathf.CeilToInt(n / 3f);
            float fl = stone.position.x - w / 2f + Face.xMin * w, fr = stone.position.x - w / 2f + Face.xMax * w;
            float ft = h / 2f - Face.yMin * h, fb = h / 2f - Face.yMax * h;
            float cellW = (fr - fl) / cols, cellH = (ft - fb) / Mathf.Max(rows, 3);
            float size = Mathf.Min(cellW, cellH) * 0.82f;
            var glow = ArenaAssets.RadialGlow();
            for (int i = 0; i < n; i++)
            {
                int c = i % cols, r = i / cols;
                var pos = new Vector3(fl + cellW * (c + 0.5f), ft - cellH * (r + 0.5f) - (cellH * (Mathf.Max(rows, 3) - rows)) * 0.5f, -0.05f);
                // Behind: an additive glow that blooms when lit. Front: a dark carved hollow that turns into a real
                // Family Stone glyph (Flow art) when the question is answered right.
                var halo = Quad("Halo" + i, glow, pos + new Vector3(0, 0, 0.01f), Vector2.one * size * 2.1f, true, true);
                var haloMat = halo.GetComponent<MeshRenderer>().sharedMaterial;
                haloMat.SetColor("_BaseColor", new Color(1f, 0.7f, 0.3f, 0f));
                var tile = Quad("Glyph" + i, ArenaAssets.Tex("glyph_" + Glyphs[i % Glyphs.Length]), pos, Vector2.one * size, true);
                var tileMat = tile.GetComponent<MeshRenderer>().sharedMaterial;
                sockets.Add(halo);
                socketMats.Add(haloMat);
                glyphMats.Add(tileMat);
                socketLit.Add(0f);
            }
        }

        void BuildFx()
        {
            var shader = ArenaAssets.Shader("particle");
            sparks = ArenaFx.Burst("Sparks", shader, new Color(1f, 0.8f, 0.35f), 0.07f, 4.5f, 0.9f);
            dust = ArenaFx.Burst("Dust", shader, new Color(0.85f, 0.75f, 0.6f, 0.5f), 0.18f, 1.2f, 1.2f);
            ArenaFx.Ambient("Motes", shader);
            confetti = ArenaFx.Confetti("Confetti", shader);
            confetti.transform.position = new Vector3(0, 4.5f, -1f);
            var light = new GameObject("Key").AddComponent<Light>();
            light.type = LightType.Directional;
            light.color = new Color(1f, 0.86f, 0.62f);
            light.transform.rotation = Quaternion.Euler(40, -20, 0);
        }

        // ------------------------------------------------------------------ HUD

        Text Label(Transform parent, string text, int size, Color color, TextAnchor anchor, bool bold = true)
        {
            var go = new GameObject("Text", typeof(RectTransform), typeof(Text), typeof(Shadow));
            go.transform.SetParent(parent, false);
            var t = go.GetComponent<Text>();
            t.font = font; t.text = text; t.fontSize = size; t.color = color; t.alignment = anchor;
            t.fontStyle = bold ? FontStyle.Bold : FontStyle.Normal;
            t.horizontalOverflow = HorizontalWrapMode.Wrap;
            t.verticalOverflow = VerticalWrapMode.Overflow;
            t.supportRichText = true;
            go.GetComponent<Shadow>().effectColor = new Color(0, 0, 0, 0.5f);
            go.GetComponent<Shadow>().effectDistance = new Vector2(0, -3);
            return t;
        }

        static RectTransform Box(Transform parent, Color color, Vector2 anchorMin, Vector2 anchorMax, Vector2 offMin, Vector2 offMax)
        {
            var go = new GameObject("Box", typeof(RectTransform), typeof(Image));
            go.transform.SetParent(parent, false);
            go.GetComponent<Image>().color = color;
            var r = (RectTransform)go.transform;
            r.anchorMin = anchorMin; r.anchorMax = anchorMax; r.offsetMin = offMin; r.offsetMax = offMax;
            return r;
        }

        static void Stretch(RectTransform r, Vector2 min, Vector2 max, Vector2 offMin, Vector2 offMax)
        {
            r.anchorMin = min; r.anchorMax = max; r.offsetMin = offMin; r.offsetMax = offMax;
        }

        void BuildHud()
        {
            var go = new GameObject("Hud", typeof(Canvas), typeof(CanvasScaler));
            var c = go.GetComponent<Canvas>();
            c.renderMode = RenderMode.ScreenSpaceOverlay;
            var scaler = go.GetComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1920, 1080);
            scaler.matchWidthOrHeight = 1f;
            canvas = (RectTransform)go.transform;

            var title = Label(canvas, "TONIGHT  ·  <color=#FFF4E0>FAMILY QUIZ NIGHT</color>", 28, Gold, TextAnchor.LowerLeft);
            Stretch(title.rectTransform, new Vector2(0.52f, 0.86f), new Vector2(0.97f, 0.86f), new Vector2(4, 12), new Vector2(0, 60));
            teamText = Label(canvas, "", 34, Cream, TextAnchor.UpperLeft);
            Stretch(teamText.rectTransform, new Vector2(0.52f, 0), new Vector2(0.97f, 0.08f), new Vector2(4, -10), new Vector2(0, -6));
            var back = Label(canvas, "BACK  home", 22, new Color(1, 1, 1, 0.55f), TextAnchor.UpperRight);
            Stretch(back.rectTransform, new Vector2(0.5f, 1), new Vector2(1, 1), new Vector2(0, -70), new Vector2(-60, -36));

            var panel = Box(canvas, Night, new Vector2(0.52f, 0.08f), new Vector2(0.97f, 0.86f), Vector2.zero, Vector2.zero);

            var avatarGo = new GameObject("Avatar", typeof(RectTransform), typeof(RawImage));
            avatarGo.transform.SetParent(panel, false);
            turnAvatar = avatarGo.GetComponent<RawImage>();
            var ar = (RectTransform)avatarGo.transform;
            ar.anchorMin = ar.anchorMax = new Vector2(0, 1); ar.pivot = new Vector2(0, 1);
            ar.anchoredPosition = new Vector2(40, -36); ar.sizeDelta = new Vector2(84, 84);
            turnText = Label(panel, "", 30, Gold, TextAnchor.UpperLeft);
            Stretch(turnText.rectTransform, new Vector2(0, 1), new Vector2(1, 1), new Vector2(144, -84), new Vector2(-40, -40));
            progressText = Label(panel, "", 22, new Color(0.66f, 0.71f, 0.8f), TextAnchor.UpperLeft, false);
            Stretch(progressText.rectTransform, new Vector2(0, 1), new Vector2(1, 1), new Vector2(144, -122), new Vector2(-40, -86));
            promptText = Label(panel, "", 58, Cream, TextAnchor.UpperLeft);
            Stretch(promptText.rectTransform, new Vector2(0, 1), new Vector2(1, 1), new Vector2(40, -330), new Vector2(-40, -160));

            choiceRoot = Box(panel, Color.clear, new Vector2(0, 0), new Vector2(1, 0), new Vector2(40, 170), new Vector2(-40, 450));
            for (int i = 0; i < 4; i++)
            {
                float x0 = (i % 2) * 0.5f, y1 = i < 2 ? 1f : 0.5f;
                var bg = Box(choiceRoot, ChoiceIdle, new Vector2(x0, y1 - 0.5f), new Vector2(x0 + 0.5f, y1), new Vector2(8, 8), new Vector2(-8, -8));
                choiceBgs.Add(bg.GetComponent<Image>());
                var t = Label(bg, "", 40, Cream, TextAnchor.MiddleLeft);
                Stretch(t.rectTransform, Vector2.zero, Vector2.one, new Vector2(28, 0), new Vector2(-16, 0));
                choiceTexts.Add(t);
            }
            feedbackText = Label(panel, "", 28, Lime, TextAnchor.UpperLeft, false);
            Stretch(feedbackText.rectTransform, new Vector2(0, 0), new Vector2(1, 0), new Vector2(40, 40), new Vector2(-40, 160));
        }

        // ------------------------------------------------------------------ flow

        int Lit => Raid.answers.FindAll(a => a.correct).Count;

        KidInfo KidFor(QuizQuestion q, int index)
        {
            var k = Raid.kids.Find(x => x.id == q.forKid);
            return k ?? (Raid.kids.Count > 0 ? Raid.kids[index % Raid.kids.Count] : new KidInfo { id = "arjun", name = "Arjun", avatar = "avatar_arjun" });
        }

        void Next()
        {
            current = Raid.questions.FindIndex(q => !Raid.answers.Exists(a => a.questionId == q.id));
            teamText.text = $"<color=#FFD166><b>{Lit}</b></color> of {Raid.questions.Count} right, as a team";
            if (current < 0) { Finish(); return; }
            var q = Raid.questions[current];
            var kid = KidFor(q, current);
            turnText.text = $"{kid.name.ToUpperInvariant()}'S TURN";
            progressText.text = $"Question {current + 1} of {Raid.questions.Count} · {(string.IsNullOrEmpty(q.skill) ? "review" : q.skill)}";
            turnAvatar.texture = ArenaAssets.Tex(string.IsNullOrEmpty(kid.avatar) ? "avatar_" + kid.id : kid.avatar);
            promptText.text = q.prompt;
            feedbackText.text = "";
            for (int i = 0; i < 4; i++)
            {
                choiceTexts[i].text = $"<color=#A9B4CC><size=28>{"ABCD"[i]}</size></color>   {(i < q.choices.Count ? q.choices[i] : "")}";
                choiceTexts[i].color = Cream;
            }
            selected = 0;
            locked = false;
            Paint();
        }

        void Paint(int answered = -1)
        {
            var q = current >= 0 ? Raid.questions[current] : null;
            for (int i = 0; i < 4; i++)
            {
                Color c = ChoiceIdle;
                if (answered >= 0 && q != null)
                {
                    if (i == q.answer) c = ChoiceRight;
                    else if (i == answered) c = ChoiceWrong;
                }
                else if (i == selected) c = ChoiceFocus;
                choiceBgs[i].color = c;
            }
        }

        void Answer(int choice)
        {
            if (locked || current < 0) return;
            locked = true;
            var q = Raid.questions[current];
            var kid = KidFor(q, current);
            bool right = choice == q.answer;
            Raid.answers.Add(new QuizAnswer { questionId = q.id, kidId = kid.id, choice = choice, correct = right });
            Paint(choice);
            feedbackText.color = right ? Lime : Cream;
            feedbackText.text = (right ? "Yes! " : "Nearly. ") + q.why;
            var at = sockets[current].position + new Vector3(0, 0, -0.3f);
            if (right)
            {
                sparks.transform.position = at; sparks.Emit(120);
                shake = 0.12f;
                StartCoroutine(Ignite(current));
                ArenaAudio.Play("crit", 0.7f);
            }
            else
            {
                dust.transform.position = at; dust.Emit(24);
                ArenaAudio.Play("hit", 0.5f);
            }
            teamText.text = $"<color=#FFD166><b>{Lit}</b></color> of {Raid.questions.Count} right, as a team";
            StartCoroutine(After(2.4f));
        }

        IEnumerator Ignite(int i)
        {
            float t = 0;
            while (t < 1f) { t += Time.deltaTime * 1.8f; socketLit[i] = Mathf.SmoothStep(0, 1, t); yield return null; }
        }

        IEnumerator After(float seconds)
        {
            yield return new WaitForSeconds(seconds);
            Next();
        }

        void Finish()
        {
            finished = true;
            confetti.Play();
            bloom.intensity.Override(1.6f);
            ArenaAudio.Play("victory");
            int lit = Lit;
            turnText.text = "QUIZ COMPLETE!";
            progressText.text = "";
            turnAvatar.enabled = false;
            promptText.text = $"{lit} of {Raid.questions.Count} right together";
            choiceRoot.gameObject.SetActive(false);
            feedbackText.color = Lime;
            feedbackText.text = (lit > 0 ? $"Everyone gets +{5 * lit} coins. Teamwork wins." : "Tough round! Next quiz has new questions.")
                                + "\n<color=#FFD166>Press OK to return</color>";
        }

        void Update()
        {
            // Sockets: dim embers until lit, then a breathing gold glow.
            for (int i = 0; i < sockets.Count; i++)
            {
                float lit = socketLit[i];
                float breathe = 0.85f + Mathf.Sin(Time.time * 2.4f + i) * 0.15f;
                bool isNext = i == current && !locked && !finished;
                var halo = new Color(1f, 0.72f, 0.32f, Mathf.Lerp(isNext ? 0.35f + 0.2f * Mathf.Sin(Time.time * 5f) : 0f, 0.9f * breathe, lit));
                socketMats[i].SetColor("_BaseColor", halo);
                // Unlit: a dark, faintly visible carving. Lit: the glyph glows at full colour.
                var tint = Color.Lerp(new Color(0.16f, 0.12f, 0.09f, isNext ? 0.95f : 0.85f), new Color(1.25f, 1.15f, 1f, 1f), lit);
                glyphMats[i].SetColor("_BaseColor", tint);
                float s = Mathf.Lerp(1f, 1.18f, lit) * (1f + 0.04f * lit * Mathf.Sin(Time.time * 2.4f + i));
                float b = BaseSize(i);
                sockets[i].localScale = new Vector3(s * b, s * b, 1);
            }
            shake = Mathf.MoveTowards(shake, 0, Time.deltaTime * 0.6f);
            var drift = new Vector3(Mathf.Sin(Time.time * 0.21f) * 0.12f, Mathf.Sin(Time.time * 0.17f) * 0.06f, 0);
            cam.transform.position = camBase + drift + (Vector3)(Random.insideUnitCircle * shake);

            if (Input.GetKeyDown(KeyCode.Escape)) { state.Finish(); return; }
            if (finished)
            {
                if (Input.GetKeyDown(KeyCode.Return) || Input.GetKeyDown(KeyCode.KeypadEnter) || Input.GetKeyDown(KeyCode.JoystickButton0)) state.Finish();
                return;
            }
            if (locked) return;
            int before = selected;
            if (Input.GetKeyDown(KeyCode.RightArrow) && selected % 2 == 0) selected += 1;
            if (Input.GetKeyDown(KeyCode.LeftArrow) && selected % 2 == 1) selected -= 1;
            if (Input.GetKeyDown(KeyCode.DownArrow) && selected < 2) selected += 2;
            if (Input.GetKeyDown(KeyCode.UpArrow) && selected >= 2) selected -= 2;
            if (selected != before) Paint();
            if (Input.GetKeyDown(KeyCode.Return) || Input.GetKeyDown(KeyCode.KeypadEnter) || Input.GetKeyDown(KeyCode.JoystickButton0)) Answer(selected);
            // Development stand-in (remote MENU): answer with the right choice, as a verified kid answer would.
            if (Input.GetKeyDown(KeyCode.Menu) || Input.GetKeyDown(KeyCode.M)) Answer(Raid.questions[current].answer);
        }

        readonly Dictionary<int, float> baseSizes = new Dictionary<int, float>();

        float BaseSize(int i)
        {
            if (!baseSizes.TryGetValue(i, out var s)) baseSizes[i] = s = sockets[i].localScale.y;
            return s;
        }
    }
}
