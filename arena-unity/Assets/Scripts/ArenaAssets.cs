using System.Collections.Generic;
using UnityEngine;

namespace Rosetta.Arena
{
    /// <summary>Art (Resources/Art/*.png, made in Google Flow) and the URP shaders the runtime-built scene uses.</summary>
    public static class ArenaAssets
    {
        static readonly Dictionary<string, Texture2D> cache = new Dictionary<string, Texture2D>();
        static Texture2D glow, dot;

        public static Texture2D Tex(string name)
        {
            if (cache.TryGetValue(name, out var t)) return t;
            t = Resources.Load<Texture2D>("Art/" + name);
            if (t == null) Debug.LogWarning("Rosetta: missing art " + name);
            cache[name] = t;
            return t;
        }

        /// <summary>Shaders are referenced by the always-included list in ArenaBuild so they survive stripping.</summary>
        public static Shader Shader(string kind)
        {
            switch (kind)
            {
                case "opaque": return UnityEngine.Shader.Find("Universal Render Pipeline/Unlit");
                case "lit": return UnityEngine.Shader.Find("Universal Render Pipeline/Lit");
                case "particle": return UnityEngine.Shader.Find("Universal Render Pipeline/Particles/Unlit");
                default: return UnityEngine.Shader.Find("Universal Render Pipeline/Unlit");
            }
        }

        /// <summary>Configures a URP Unlit/Particles material for alpha blending (code-built materials default to opaque).</summary>
        public static void MakeTransparent(Material m, bool additive = false)
        {
            m.SetFloat("_Surface", 1f);
            m.SetFloat("_Blend", additive ? 2f : 0f);
            m.SetOverrideTag("RenderType", "Transparent");
            m.SetInt("_SrcBlend", (int)UnityEngine.Rendering.BlendMode.SrcAlpha);
            m.SetInt("_DstBlend", (int)(additive ? UnityEngine.Rendering.BlendMode.One : UnityEngine.Rendering.BlendMode.OneMinusSrcAlpha));
            m.SetInt("_ZWrite", 0);
            m.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");
            m.renderQueue = (int)UnityEngine.Rendering.RenderQueue.Transparent;
        }

        public static Texture2D RadialGlow() => glow ??= Radial(128, 2.2f);
        public static Texture2D SoftDot() => dot ??= Radial(32, 1.4f);

        static Texture2D Radial(int size, float power)
        {
            var t = new Texture2D(size, size, TextureFormat.RGBA32, false) { wrapMode = TextureWrapMode.Clamp };
            var px = new Color[size * size];
            for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
            {
                float dx = (x + 0.5f) / size * 2 - 1, dy = (y + 0.5f) / size * 2 - 1;
                float a = Mathf.Pow(Mathf.Clamp01(1 - Mathf.Sqrt(dx * dx + dy * dy)), power);
                px[y * size + x] = new Color(1, 1, 1, a);
            }
            t.SetPixels(px);
            t.Apply();
            return t;
        }
    }
}
