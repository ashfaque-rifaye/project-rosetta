using UnityEngine;

namespace Rosetta.Arena
{
    /// <summary>Code-built particle systems: hit sparks, puffs, ambient dust motes and victory confetti.</summary>
    public static class ArenaFx
    {
        static ParticleSystem Make(string name, Shader shader, bool additive, out ParticleSystemRenderer r)
        {
            var go = new GameObject(name);
            var ps = go.AddComponent<ParticleSystem>();
            ps.Stop(true, ParticleSystemStopBehavior.StopEmittingAndClear);
            r = go.GetComponent<ParticleSystemRenderer>();
            var m = new Material(shader);
            m.mainTexture = ArenaAssets.SoftDot();
            m.SetTexture("_BaseMap", ArenaAssets.SoftDot());
            ArenaAssets.MakeTransparent(m, additive);
            r.sharedMaterial = m;
            var main = ps.main;
            main.playOnAwake = false;
            main.simulationSpace = ParticleSystemSimulationSpace.World;
            var em = ps.emission;
            em.rateOverTime = 0;
            return ps;
        }

        public static ParticleSystem Burst(string name, Shader shader, Color color, float size, float speed, float life)
        {
            var ps = Make(name, shader, true, out var r);
            var main = ps.main;
            main.startColor = new ParticleSystem.MinMaxGradient(color, Color.Lerp(color, Color.white, 0.6f));
            main.startSize = new ParticleSystem.MinMaxCurve(size * 0.5f, size * 1.5f);
            main.startSpeed = new ParticleSystem.MinMaxCurve(speed * 0.4f, speed);
            main.startLifetime = new ParticleSystem.MinMaxCurve(life * 0.5f, life);
            main.gravityModifier = 0.6f;
            main.maxParticles = 1500;
            var shape = ps.shape;
            shape.shapeType = ParticleSystemShapeType.Sphere;
            shape.radius = 0.5f;
            var col = ps.colorOverLifetime;
            col.enabled = true;
            var g = new Gradient();
            g.SetKeys(new[] { new GradientColorKey(Color.white, 0), new GradientColorKey(Color.white, 1) },
                      new[] { new GradientAlphaKey(1, 0), new GradientAlphaKey(0, 1) });
            col.color = g;
            var sz = ps.sizeOverLifetime;
            sz.enabled = true;
            sz.size = new ParticleSystem.MinMaxCurve(1f, AnimationCurve.Linear(0, 1, 1, 0.2f));
            r.renderMode = ParticleSystemRenderMode.Stretch;
            r.velocityScale = 0.04f;
            r.lengthScale = 1.2f;
            ps.Play();
            return ps;
        }

        public static ParticleSystem Ambient(string name, Shader shader)
        {
            var ps = Make(name, shader, true, out _);
            ps.transform.position = new Vector3(0, 1.2f, 0f);
            var main = ps.main;
            main.startColor = new Color(1f, 0.9f, 0.7f, 0.35f);
            main.startSize = new ParticleSystem.MinMaxCurve(0.03f, 0.09f);
            main.startSpeed = 0.08f;
            main.startLifetime = 9f;
            main.maxParticles = 220;
            main.prewarm = true;
            main.loop = true;
            var em = ps.emission;
            em.rateOverTime = 22;
            var shape = ps.shape;
            shape.shapeType = ParticleSystemShapeType.Box;
            shape.scale = new Vector3(16, 8, 6);
            var noise = ps.noise;
            noise.enabled = true;
            noise.strength = 0.25f;
            noise.frequency = 0.3f;
            ps.Play();
            return ps;
        }

        public static ParticleSystem Confetti(string name, Shader shader)
        {
            var ps = Make(name, shader, false, out var r);
            var main = ps.main;
            main.startColor = new ParticleSystem.MinMaxGradient(new Gradient
            {
                colorKeys = new[]
                {
                    new GradientColorKey(new Color(1f, 0.82f, 0.25f), 0), new GradientColorKey(new Color(0.9f, 0.28f, 0.5f), 0.33f),
                    new GradientColorKey(new Color(0.24f, 0.59f, 0.82f), 0.66f), new GradientColorKey(new Color(0.45f, 0.6f, 0.06f), 1),
                },
            }) { mode = ParticleSystemGradientMode.RandomColor };
            main.startSize = new ParticleSystem.MinMaxCurve(0.08f, 0.16f);
            main.startSpeed = new ParticleSystem.MinMaxCurve(1f, 4f);
            main.startLifetime = 5f;
            main.startRotation = new ParticleSystem.MinMaxCurve(0, Mathf.PI * 2);
            main.gravityModifier = 0.25f;
            main.maxParticles = 1200;
            main.loop = true;
            var em = ps.emission;
            em.rateOverTime = 180;
            var shape = ps.shape;
            shape.shapeType = ParticleSystemShapeType.Box;
            shape.scale = new Vector3(14, 0.2f, 3);
            var rot = ps.rotationOverLifetime;
            rot.enabled = true;
            rot.z = new ParticleSystem.MinMaxCurve(-4f, 4f);
            r.renderMode = ParticleSystemRenderMode.Billboard;
            return ps;
        }
    }
}
