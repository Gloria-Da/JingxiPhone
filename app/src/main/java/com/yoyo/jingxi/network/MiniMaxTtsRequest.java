package com.yoyo.jingxi.network;

public class MiniMaxTtsRequest {
    public String model;
    public String text;
    public boolean stream;
    public VoiceSetting voice_setting;
    public AudioSetting audio_setting;
    public VoiceModify voice_modify;

    public MiniMaxTtsRequest(String model, String text, String voiceId, int voicePitch, int voiceIntensity, int voiceTimbre, String soundEffect, float voiceSpeed, String emotion) {
        this.model = model;
        this.text = text;
        this.stream = false; // 非流式
        this.voice_setting = new VoiceSetting();
        this.voice_setting.voice_id = voiceId;
        if (emotion != null && !emotion.isEmpty()) {
            this.voice_setting.emotion = emotion;
        }
        this.audio_setting = new AudioSetting();
        this.audio_setting.speed = voiceSpeed;
        
        if (voicePitch != 0 || voiceIntensity != 0 || voiceTimbre != 0 || (soundEffect != null && !soundEffect.isEmpty())) {
            this.voice_modify = new VoiceModify();
            this.voice_modify.pitch = voicePitch;
            this.voice_modify.intensity = voiceIntensity;
            this.voice_modify.timbre = voiceTimbre;
            if (soundEffect != null && !soundEffect.isEmpty() && !soundEffect.equals("无")) {
                this.voice_modify.sound_effects = soundEffect;
            }
        }
    }

    public static class VoiceSetting {
        public String voice_id;
        public float speed = 1.0f;
        public float vol = 1.0f;
        public int pitch = 0;
        public String emotion;
    }

    public static class AudioSetting {
        public int sample_rate = 32000;
        public int bitrate = 128000;
        public String format = "mp3";
        public int channel = 1;
        public float speed = 1.0f;
    }

    public static class VoiceModify {
        public int pitch = 0;
        public int intensity = 0;
        public int timbre = 0;
        public String sound_effects;
    }
}
