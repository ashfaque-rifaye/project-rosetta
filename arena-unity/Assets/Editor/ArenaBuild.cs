using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.Android;
using UnityEditor.Build.Reporting;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;

namespace Rosetta.Arena.Editor
{
    /// <summary>
    /// Headless project setup + Fire OS APK build:
    ///   Unity.exe -batchmode -quit -projectPath RosettaArena -executeMethod Rosetta.Arena.Editor.ArenaBuild.Apk
    /// Creates the URP asset, the one-object scene, imports art settings, and builds ARMv7 + ARM64 + x86_64.
    /// </summary>
    public static class ArenaBuild
    {
        const string ScenePath = "Assets/Scenes/Arena.unity";
        const string UrpPath = "Assets/Settings/ArenaURP.asset";

        [MenuItem("Rosetta/Setup project")]
        public static void Setup()
        {
            Directory.CreateDirectory("Assets/Settings");
            Directory.CreateDirectory("Assets/Scenes");

            // URP pipeline asset with post-processing (bloom/vignette/chromatic aberration come from a runtime Volume).
            var urp = AssetDatabase.LoadAssetAtPath<UniversalRenderPipelineAsset>(UrpPath);
            if (urp == null)
            {
                var renderer = ScriptableObject.CreateInstance<UniversalRendererData>();
                AssetDatabase.CreateAsset(renderer, "Assets/Settings/ArenaRenderer.asset");
                urp = UniversalRenderPipelineAsset.Create(renderer);
                AssetDatabase.CreateAsset(urp, UrpPath);
            }
            urp.supportsHDR = true;
            urp.msaaSampleCount = 1;
            urp.renderScale = 1f;
            GraphicsSettings.defaultRenderPipeline = urp;
            for (int i = 0; i < QualitySettings.names.Length; i++)
            {
                QualitySettings.SetQualityLevel(i, false);
                QualitySettings.renderPipeline = urp;
            }

            // Art: full-resolution, no mips, clamp; the boss PNGs carry their feathered alpha.
            foreach (var guid in AssetDatabase.FindAssets("t:Texture2D", new[] { "Assets/Resources/Art" }))
            {
                var path = AssetDatabase.GUIDToAssetPath(guid);
                if (AssetImporter.GetAtPath(path) is TextureImporter ti)
                {
                    ti.textureType = TextureImporterType.Default;
                    ti.mipmapEnabled = false;
                    ti.alphaIsTransparency = true;
                    ti.wrapMode = TextureWrapMode.Clamp;
                    ti.maxTextureSize = 2048;
                    ti.textureCompression = TextureImporterCompression.CompressedHQ;
                    ti.SaveAndReimport();
                }
            }

            // Shaders created from code must be kept in the player build.
            var gs = AssetDatabase.LoadAssetAtPath<Object>("ProjectSettings/GraphicsSettings.asset");
            var so = new SerializedObject(gs);
            var list = so.FindProperty("m_AlwaysIncludedShaders");
            foreach (var name in new[] { "Universal Render Pipeline/Unlit", "Universal Render Pipeline/Lit", "Universal Render Pipeline/Particles/Unlit" })
            {
                var sh = Shader.Find(name);
                if (sh == null || Enumerable.Range(0, list.arraySize).Any(i => list.GetArrayElementAtIndex(i).objectReferenceValue == sh)) continue;
                list.InsertArrayElementAtIndex(list.arraySize);
                list.GetArrayElementAtIndex(list.arraySize - 1).objectReferenceValue = sh;
            }
            so.ApplyModifiedProperties();

            // One scene, one object: ArenaDirector builds everything else at runtime.
            var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);
            new GameObject("Arena").AddComponent<ArenaDirector>();
            RenderSettings.ambientLight = new Color(0.45f, 0.45f, 0.55f);
            EditorSceneManager.SaveScene(scene, ScenePath);
            EditorBuildSettings.scenes = new[] { new EditorBuildSettingsScene(ScenePath, true) };

            // Player: Fire TV is landscape-only, leanback launcher, no touchscreen.
            PlayerSettings.companyName = "Project Rosetta";
            PlayerSettings.productName = "Rosetta Arena";
            PlayerSettings.SetApplicationIdentifier(UnityEditor.Build.NamedBuildTarget.Android, "app.rosetta.arena");
            PlayerSettings.bundleVersion = "1.0";
            PlayerSettings.Android.bundleVersionCode = 1;
            PlayerSettings.defaultInterfaceOrientation = UIOrientation.LandscapeLeft;
            PlayerSettings.Android.minSdkVersion = AndroidSdkVersions.AndroidApiLevel25;
            PlayerSettings.Android.androidTVCompatibility = true;
            PlayerSettings.Android.targetArchitectures = AndroidArchitecture.ARMv7 | AndroidArchitecture.ARM64 | AndroidArchitecture.X86_64; // ARMv7: 32-bit Fire TV sticks and Android TVs
            PlayerSettings.SetScriptingBackend(UnityEditor.Build.NamedBuildTarget.Android, ScriptingImplementation.IL2CPP);
            PlayerSettings.SetGraphicsAPIs(BuildTarget.Android, new[] { GraphicsDeviceType.OpenGLES3 });
            PlayerSettings.SetUseDefaultGraphicsAPIs(BuildTarget.Android, false);
            PlayerSettings.colorSpace = ColorSpace.Linear;
            PlayerSettings.stripEngineCode = false; // colliders, particles and post FX are only created from code
            PlayerSettings.SplashScreen.showUnityLogo = true;
            PlayerSettings.SplashScreen.backgroundColor = new Color(0.04f, 0.06f, 0.13f);
            var banner = AssetDatabase.LoadAssetAtPath<Texture2D>("Assets/Resources/Art/arena.png");
            if (banner != null) PlayerSettings.SetIconsForTargetGroup(BuildTargetGroup.Unknown, new[] { banner });

            AssetDatabase.SaveAssets();
            Debug.Log("Rosetta: project setup done");
        }

        /// <summary>Android toolchain: env vars win, else the E: layout used in development, else Unity's bundled modules.</summary>
        static void AndroidTools()
        {
            string Pick(string env, string fallback)
            {
                var v = System.Environment.GetEnvironmentVariable(env);
                return !string.IsNullOrEmpty(v) ? v : Directory.Exists(fallback) ? fallback : null;
            }
            var sdk = Pick("ROSETTA_ANDROID_SDK", Path.Combine(System.Environment.GetFolderPath(System.Environment.SpecialFolder.LocalApplicationData), "Android", "Sdk"));
            var ndk = Pick("ROSETTA_ANDROID_NDK", @"E:\Unity\AndroidTools\android-ndk-r27c");
            var jdk = Pick("ROSETTA_JDK", @"E:\Unity\AndroidTools\jdk");
            if (sdk != null) AndroidExternalToolsSettings.sdkRootPath = sdk;
            if (ndk != null) AndroidExternalToolsSettings.ndkRootPath = ndk;
            if (jdk != null) AndroidExternalToolsSettings.jdkRootPath = jdk;
            Debug.Log($"Rosetta: Android SDK={sdk} NDK={ndk} JDK={jdk}");
        }

        public static void Apk()
        {
            AndroidTools();
            Setup();
            EditorUserBuildSettings.SwitchActiveBuildTarget(BuildTargetGroup.Android, BuildTarget.Android);
            EditorUserBuildSettings.buildAppBundle = false;
            Directory.CreateDirectory("Builds");
            var report = BuildPipeline.BuildPlayer(new BuildPlayerOptions
            {
                scenes = new[] { ScenePath },
                locationPathName = "Builds/RosettaArena.apk",
                target = BuildTarget.Android,
                options = BuildOptions.None,
            });
            Debug.Log($"Rosetta: build {report.summary.result}, {report.summary.totalSize / (1024 * 1024)} MB, {report.summary.totalErrors} errors");
            if (report.summary.result != BuildResult.Succeeded) EditorApplication.Exit(1);
        }
    }
}
