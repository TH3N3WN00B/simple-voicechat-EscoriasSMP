package de.maxhenkel.voicechat.plugins.impl.opus;

import de.maxhenkel.opus4j.OpusEncoder.Application;
import de.maxhenkel.voicechat.Voicechat;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;
import org.concentus.OpusApplication;

public class OpusManager {

    public static final int SAMPLE_RATE = 48000;
    public static final int FRAME_SIZE = (SAMPLE_RATE / 1000) * 20;

    public static OpusEncoder createEncoder(int sampleRate, int frameSize, int maxPayloadSize, OpusApplication application) {
        try {
            NativeOpusEncoderImpl encoder = new NativeOpusEncoderImpl(sampleRate, 1, toNativeApplication(application));
            encoder.setMaxPayloadSize(maxPayloadSize);
            return encoder;
        } catch (Throwable e) {
            Voicechat.LOGGER.warn("Failed to load native Opus encoder - Falling back to Java Opus implementation", e);
        }
        return new JavaOpusEncoderImpl(sampleRate, frameSize, maxPayloadSize, application);
    }

    public static OpusEncoder createEncoder(OpusEncoderMode mode) {
        OpusApplication application = OpusApplication.OPUS_APPLICATION_VOIP;
        if (mode != null) {
            switch (mode) {
                case AUDIO:
                    application = OpusApplication.OPUS_APPLICATION_AUDIO;
                    break;
                case RESTRICTED_LOWDELAY:
                    application = OpusApplication.OPUS_APPLICATION_RESTRICTED_LOWDELAY;
                    break;
                default:
                    break;
            }

        }
        return createEncoder(SAMPLE_RATE, FRAME_SIZE, 1024, application);
    }

    public static OpusDecoder createDecoder(int sampleRate, int frameSize) {
        try {
            NativeOpusDecoderImpl decoder = new NativeOpusDecoderImpl(sampleRate, 1);
            decoder.setFrameSize(frameSize);
            return decoder;
        } catch (Throwable e) {
            Voicechat.LOGGER.warn("Failed to load native Opus decoder - Falling back to Java Opus implementation", e);
        }
        return new JavaOpusDecoderImpl(sampleRate, frameSize);
    }

    public static OpusDecoder createDecoder() {
        return createDecoder(SAMPLE_RATE, FRAME_SIZE);
    }

    private static Application toNativeApplication(OpusApplication application) {
        if (application == OpusApplication.OPUS_APPLICATION_AUDIO) {
            return Application.AUDIO;
        } else if (application == OpusApplication.OPUS_APPLICATION_RESTRICTED_LOWDELAY) {
            return Application.LOW_DELAY;
        } else {
            return Application.VOIP;
        }
    }

}