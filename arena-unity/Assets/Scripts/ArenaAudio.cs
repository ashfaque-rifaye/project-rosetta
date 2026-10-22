using System.Collections.Generic;
using UnityEngine;

namespace Rosetta.Arena
{
    /// <summary>The TV app's synthesized SFX (Resources/Sfx/*.wav) played through one shared source.</summary>
    public static class ArenaAudio
    {
        static AudioSource src;
        static readonly Dictionary<string, AudioClip> clips = new Dictionary<string, AudioClip>();

        public static void Play(string name, float volume = 1f)
        {
            if (src == null)
            {
                var go = new GameObject("Audio");
                Object.DontDestroyOnLoad(go);
                src = go.AddComponent<AudioSource>();
            }
            if (!clips.TryGetValue(name, out var clip)) clips[name] = clip = Resources.Load<AudioClip>("Sfx/" + name);
            if (clip != null) src.PlayOneShot(clip, volume);
        }
    }
}
