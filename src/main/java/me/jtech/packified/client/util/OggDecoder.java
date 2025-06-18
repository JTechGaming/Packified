package me.jtech.packified.client.util;

import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import static org.lwjgl.stb.STBVorbis.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;

public class OggDecoder {

    public static DecodedAudio decodeOgg(byte[] audioData) {
        ByteBuffer vorbisBuffer = memAlloc(audioData.length);
        vorbisBuffer.put(audioData).flip();

        try (var stack = stackPush()) {
            IntBuffer error = stack.mallocInt(1);
            long decoder = stb_vorbis_open_memory(vorbisBuffer, error, null);
            if (decoder == NULL) {
                throw new RuntimeException("Failed to open OGG stream. Error: " + error.get(0));
            }

            STBVorbisInfo info = STBVorbisInfo.mallocStack(stack);
            stb_vorbis_get_info(decoder, info);

            int channels = info.channels();
            int sampleRate = info.sample_rate();

            int totalSamples = stb_vorbis_stream_length_in_samples(decoder);
            ShortBuffer pcm = memAllocShort(totalSamples * channels);

            int samples = stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm) * channels;

            float[] waveform = new float[samples];
            for (int i = 0; i < samples; i++) {
                waveform[i] = pcm.get(i) / 32768f;
            }

            stb_vorbis_close(decoder);
            memFree(pcm);

            return new DecodedAudio(waveform, sampleRate, channels);
        } finally {
            memFree(vorbisBuffer);
        }
    }

    public static class DecodedAudio {
        public final float[] waveform;
        public final int sampleRate;
        public final int channels;

        public DecodedAudio(float[] waveform, int sampleRate, int channels) {
            this.waveform = waveform;
            this.sampleRate = sampleRate;
            this.channels = channels;
        }
    }
}

