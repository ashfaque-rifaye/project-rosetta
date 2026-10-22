using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;

namespace Rosetta.Arena
{
    /// <summary>
    /// Builds and directs the whole raid scene at runtime: 2.5D parallax arena, the boss, particles, physics debris,
    /// post-processing, camera work and the HUD. Everything is code so the arena builds headlessly (batch mode).
    /// </summary>
    public class ArenaDirector : MonoBehaviour
    {
        ArenaState state;
        Camera cam;
        Transform boss;
        Material bossMat;
        Vector3 bossBase;
        ParticleSystem sparks, motes, confetti, smoke;
        Volume volume;
        ChromaticAberration chroma;
        Bloom bloom;
        Vignette vignette;
        ArenaHud hud;
        Material[] debrisMats;
        float shake, squash = 1f, flash;
        Vector3 camBase;
        const float BossH = 5.0f; // world units at z=2: about half the frame, leaving the playroom visible around it
        bool ending;

        void Awake()
        {
            Application.targetFrameRate = 60;
            QualitySettings.vSyncCount = 0;
            state = gameObject.AddComponent<ArenaState>();
            if (state.Raid.mode != "raid")
            {
                // The family Quiz Battle (Stone Trial) replaced the Mess Boss raid on 22 Oct.
                gameObject.AddComponent<StoneTrial>().Init(state);
                enabled = false;
                return;
            }
            BuildCamera();
            BuildWorld();
            BuildParticles();
            BuildPostFx();
            hud = gameObject.AddComponent<ArenaHud>();
            hud.Init(state);
            state.OnHit += HandleHit;
            state.OnDefeated += () => StartCoroutine(Victory());
        }

        // ------------------------------------------------------------------ scene construction

        void BuildCamera()
        {
            var go = new GameObject("Camera");
            cam = go.AddComponent<Camera>();
            cam.clearFlags = CameraClearFlags.SolidColor;
            cam.backgroundColor = new Color(0.04f, 0.06f, 0.13f);
            cam.fieldOfView = 38f;
            cam.transform.position = camBase = new Vector3(0, 1.1f, -10f);
            cam.transform.LookAt(new Vector3(0, 1.0f, 0));
            var data = go.AddComponent<UniversalAdditionalCameraData>();
            data.renderPostProcessing = true;
            data.antialiasing = AntialiasingMode.FastApproximateAntialiasing;
        }

        Material Unlit(Texture2D tex, bool transparent = true)
        {
            var m = new Material(ArenaAssets.Shader(transparent ? "transparent" : "opaque"));
            m.mainTexture = tex;
            m.SetTexture("_BaseMap", tex);
            if (transparent) ArenaAssets.MakeTransparent(m);
            return m;
        }

        GameObject Quad(string name, Texture2D tex, Vector3 pos, float height, bool transparent = true)
        {
            var q = GameObject.CreatePrimitive(PrimitiveType.Quad);
            q.name = name;
            Destroy(q.GetComponent<Collider>());
            float aspect = tex ? (float)tex.width / tex.height : 1f;
            q.transform.position = pos;
            q.transform.localScale = new Vector3(height * aspect, height, 1);
            q.GetComponent<MeshRenderer>().sharedMaterial = Unlit(tex, transparent);
            return q;
        }

        void BuildWorld()
        {
            // Far: the stop-motion playroom, sized to cover the whole frustum (plus drift/shake margin) at its depth.
            var arenaTex = ArenaAssets.Tex("arena");
            float dist = 14f - camBase.z;
            float fillH = 2f * dist * Mathf.Tan(cam.fieldOfView * 0.5f * Mathf.Deg2Rad) * 1.12f;
            float fillW = fillH * cam.aspect;
            float arenaAspect = arenaTex ? (float)arenaTex.width / arenaTex.height : 1f;
            Quad("Arena", arenaTex, new Vector3(0, 1.1f, 14f), Mathf.Max(fillH, fillW / arenaAspect), false);

            // The boss: feathered cut-out on a quad, lit by a warm/rose rim glow behind it.
            var glow = Quad("BossGlow", ArenaAssets.RadialGlow(), new Vector3(0, 0.8f, 2.2f), BossH * 1.5f);
            var glowMat = glow.GetComponent<MeshRenderer>().sharedMaterial;
            ArenaAssets.MakeTransparent(glowMat, true);
            glowMat.SetColor("_BaseColor", new Color(1f, 0.35f, 0.5f, 0.55f));
            var b = Quad("Boss", ArenaAssets.Tex("boss_" + state.Raid.bossId), new Vector3(0, 0.6f, 2f), BossH);
            boss = b.transform;
            bossBase = boss.position;
            bossMat = b.GetComponent<MeshRenderer>().sharedMaterial;

            // Floor collider for physics debris + a dark reflective-looking floor plane.
            var floor = GameObject.CreatePrimitive(PrimitiveType.Plane);
            floor.name = "Floor";
            floor.transform.position = new Vector3(0, -2.1f, 3f);
            floor.transform.localScale = new Vector3(4, 1, 2);
            floor.GetComponent<MeshRenderer>().enabled = false;

            debrisMats = new[]
            {
                Solid(new Color(0.95f, 0.30f, 0.30f)), Solid(new Color(0.25f, 0.55f, 0.95f)), Solid(new Color(1f, 0.82f, 0.25f)),
                Solid(new Color(0.45f, 0.80f, 0.35f)), Solid(new Color(0.95f, 0.95f, 0.92f)),
            };
        }

        Material Solid(Color c)
        {
            var m = new Material(ArenaAssets.Shader("lit"));
            m.color = c;
            m.SetColor("_BaseColor", c);
            m.SetFloat("_Smoothness", 0.35f);
            return m;
        }

        void BuildParticles()
        {
            var light = new GameObject("KeyLight").AddComponent<Light>();
            light.type = LightType.Directional;
            light.color = new Color(1f, 0.86f, 0.62f);
            light.intensity = 1.3f;
            light.transform.rotation = Quaternion.Euler(50, -30, 0);

            sparks = ArenaFx.Burst("HitSparks", ArenaAssets.Shader("particle"), new Color(1f, 0.85f, 0.4f), 0.12f, 9f, 0.6f);
            sparks.transform.position = bossBase + new Vector3(0, 0.6f, -1.2f);
            smoke = ArenaFx.Burst("HitPuff", ArenaAssets.Shader("particle"), new Color(1f, 1f, 1f, 0.35f), 0.9f, 2.2f, 1.1f);
            smoke.transform.position = sparks.transform.position;
            motes = ArenaFx.Ambient("DustMotes", ArenaAssets.Shader("particle"));
            confetti = ArenaFx.Confetti("Confetti", ArenaAssets.Shader("particle"));
            confetti.transform.position = new Vector3(0, 6.5f, 1f);
        }

        void BuildPostFx()
        {
            var go = new GameObject("PostFx");
            volume = go.AddComponent<Volume>();
            volume.isGlobal = true;
            var profile = ScriptableObject.CreateInstance<VolumeProfile>();
            bloom = profile.Add<Bloom>(true);
            bloom.intensity.Override(0.9f);
            bloom.threshold.Override(0.85f);
            bloom.scatter.Override(0.7f);
            vignette = profile.Add<Vignette>(true);
            vignette.intensity.Override(0.38f);
            vignette.smoothness.Override(0.45f);
            chroma = profile.Add<ChromaticAberration>(true);
            chroma.intensity.Override(0f);
            var color = profile.Add<ColorAdjustments>(true);
            color.saturation.Override(12f);
            color.contrast.Override(8f);
            volume.profile = profile;
        }

        // ------------------------------------------------------------------ the fight

        void HandleHit(HitInfo hit)
        {
            shake = hit.crit ? 0.35f : 0.18f;
            squash = 0.82f;
            flash = hit.crit ? 1f : 0.6f;
            sparks.Emit(hit.crit ? 90 : 45);
            smoke.Emit(8);
            SpawnDebris(hit.crit ? 14 : 7);
            hud.ShowDamage(hit, cam.WorldToScreenPoint(bossBase + new Vector3(Random.Range(-1.4f, 1.4f), 2.2f, 0)));
            if (hit.crit) StartCoroutine(SlowMo(0.25f, 0.45f));
            ArenaAudio.Play(hit.crit ? "crit" : "hit");
        }

        void SpawnDebris(int n)
        {
            for (int i = 0; i < n; i++)
            {
                bool sock = Random.value < 0.5f;
                var go = GameObject.CreatePrimitive(sock ? PrimitiveType.Capsule : PrimitiveType.Cube);
                go.transform.localScale = sock ? new Vector3(0.16f, 0.32f, 0.16f) : new Vector3(0.28f, 0.18f, 0.18f);
                go.transform.position = bossBase + new Vector3(Random.Range(-1.2f, 1.2f), Random.Range(0.2f, 2f), -1.5f);
                go.transform.rotation = Random.rotation;
                go.GetComponent<MeshRenderer>().sharedMaterial = debrisMats[Random.Range(0, debrisMats.Length)];
                var rb = go.AddComponent<Rigidbody>();
                rb.mass = 0.2f;
                rb.AddForce(new Vector3(Random.Range(-4f, 4f), Random.Range(4f, 8f), Random.Range(-4f, -1f)), ForceMode.Impulse);
                rb.AddTorque(Random.insideUnitSphere * 6f, ForceMode.Impulse);
                Destroy(go, 4f);
            }
        }

        IEnumerator SlowMo(float scale, float seconds)
        {
            Time.timeScale = scale;
            chroma.intensity.Override(0.8f);
            yield return new WaitForSecondsRealtime(seconds);
            Time.timeScale = 1f;
            chroma.intensity.Override(0f);
        }

        IEnumerator Victory()
        {
            ending = true;
            yield return SlowMo(0.15f, 1.2f);
            ArenaAudio.Play("victory");
            for (int i = 0; i < 6; i++) { sparks.Emit(120); SpawnDebris(18); shake = 0.4f; yield return new WaitForSecondsRealtime(0.18f); }
            confetti.Play();
            float t = 0;
            while (t < 1f) { t += Time.unscaledDeltaTime * 1.2f; boss.localScale = Vector3.Lerp(boss.localScale, Vector3.zero, t); yield return null; }
            boss.gameObject.SetActive(false);
            bloom.intensity.Override(1.6f);
            hud.ShowVictory();
        }

        void Update()
        {
            // Idle "breathing" + hit squash + red flash on the boss.
            if (!ending)
            {
                float breathe = Mathf.Sin(Time.time * 2.2f) * 0.06f;
                squash = Mathf.Lerp(squash, 1f, Time.unscaledDeltaTime * 9f);
                boss.position = bossBase + new Vector3(0, breathe, 0);
                var s = boss.localScale;
                float baseH = BossH, baseW = BossH * ((float)bossMat.mainTexture.width / bossMat.mainTexture.height);
                boss.localScale = new Vector3(baseW * (2f - squash), baseH * squash, s.z);
            }
            flash = Mathf.MoveTowards(flash, 0f, Time.unscaledDeltaTime * 3f);
            var tint = Color.Lerp(Color.white, new Color(1f, 0.35f, 0.35f), flash);
            bossMat.color = tint;
            bossMat.SetColor("_BaseColor", tint);

            // Camera: slow drift + shake.
            shake = Mathf.MoveTowards(shake, 0f, Time.unscaledDeltaTime * 1.2f);
            var drift = new Vector3(Mathf.Sin(Time.unscaledTime * 0.25f) * 0.35f, Mathf.Sin(Time.unscaledTime * 0.18f) * 0.12f, 0);
            cam.transform.position = camBase + drift + (Vector3)(Random.insideUnitCircle * shake);
            cam.transform.LookAt(new Vector3(drift.x * 0.4f, 1.0f, 0));

            // Fire TV remote: MENU = dev stand-in for a verified put-away; BACK = return to the TV app.
            if (Input.GetKeyDown(KeyCode.Menu) || Input.GetKeyDown(KeyCode.M) || Input.GetKeyDown(KeyCode.JoystickButton2)) state.DevHit();
            if (Input.GetKeyDown(KeyCode.Escape)) state.Finish();
            if (state.Defeated && (Input.GetKeyDown(KeyCode.Return) || Input.GetKeyDown(KeyCode.JoystickButton0) || Input.GetKeyDown(KeyCode.KeypadEnter))) state.Finish();
        }
    }
}
