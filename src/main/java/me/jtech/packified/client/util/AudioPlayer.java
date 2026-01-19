package me.jtech.packified.client.util;

import me.jtech.packified.Packified;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.AlUtil;
import net.minecraft.client.sound.Source;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;

import java.nio.ShortBuffer;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL11.AL_SEC_OFFSET;
import static org.lwjgl.system.MemoryUtil.NULL;

@Environment(EnvType.CLIENT)
public class AudioPlayer {
    private int sourceId = -1;
    private int bufferId = -1;
    private int sampleRate;
    private int totalSamples;
    public boolean loaded = false;

    static boolean checkErrors(String sectionName) {
        int i = AL10.alGetError();
        if (i != 0) {
            Packified.LOGGER.error("{}: {}", sectionName, getErrorMessage(i));
            return true;
        } else {
            return false;
        }
    }

    private static String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case 40961 -> {
                return "Invalid name parameter.";
            }
            case 40962 -> {
                return "Invalid enumerated parameter value.";
            }
            case 40963 -> {
                return "Invalid parameter parameter value.";
            }
            case 40964 -> {
                return "Invalid operation.";
            }
            case 40965 -> {
                return "Unable to allocate memory.";
            }
            default -> {
                return "An unrecognized error occurred.";
            }
        }
    }

    public void load(float[] waveform, int sampleRate, int channels) {
        // Clean up any previous buffer/source to avoid leaking or mixing state
        cleanup();

        this.sampleRate = Math.max(1, sampleRate);

        // Create OpenAL buffer and source
        bufferId = alGenBuffers();
        if (checkErrors("alGenBuffers")) {
            bufferId = -1;
            return;
        }
        sourceId = alGenSources();
        if (checkErrors("alGenSources")) {
            // cleanup partial allocations
            if (bufferId != -1) {
                alDeleteBuffers(bufferId);
                bufferId = -1;
            }
            sourceId = -1;
            return;
        }

        // Convert float array to PCM16 direct ShortBuffer (safe clamping)
        ShortBuffer pcmBuffer = BufferUtils.createShortBuffer(waveform.length);
        for (float sample : waveform) {
            // clamp and round properly to avoid overflow / very large values
            int v = Math.round(sample * 32767f);
            if (v > Short.MAX_VALUE) v = Short.MAX_VALUE;
            if (v < Short.MIN_VALUE) v = Short.MIN_VALUE;
            pcmBuffer.put((short) v);
        }
        pcmBuffer.flip();

        // Compute frames (totalSamples = frames, i.e., samples per channel)
        int frames = Math.max(0, pcmBuffer.limit() / Math.max(1, channels));
        this.totalSamples = frames;

        // Determine OpenAL format (clamp channels to 1 or 2)
        int ch = Math.max(1, Math.min(2, channels));
        int format = (ch == 1) ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;

        // Upload data
        alBufferData(bufferId, format, pcmBuffer, this.sampleRate);
        if (checkErrors("alBufferData")) {
            // abort and cleanup on error
            cleanup();
            return;
        }

        // Attach buffer to source
        alSourcei(sourceId, AL_BUFFER, bufferId);
        checkErrors("alSourcei");

        // Explicit source parameters
        alSourcef(sourceId, AL_GAIN, 1.0f);
        alSourcei(sourceId, AL_LOOPING, AL_FALSE);

        loaded = true;
    }

    public void play() {
        if (sourceId != -1) {
            alSourcePlay(sourceId);
            checkErrors("alSourcePlay");
        }
    }

    public void pause() {
        if (sourceId != -1) {
            alSourcePause(sourceId);
            checkErrors("alSourcePause");
        }
    }

    public void stop() {
        if (sourceId != -1) {
            alSourceStop(sourceId);
            checkErrors("alSourceStop");
            cleanup();
        }
    }

    public boolean isPlaying() {
        if (sourceId == -1) return false;
        int state = alGetSourcei(sourceId, AL_SOURCE_STATE);
        checkErrors("alGetSourcei");
        return state == AL_PLAYING;
    }

    public float getPlayheadPosition() {
        if (sourceId == -1) return 0.0f;
        if (sampleRate <= 0 || totalSamples <= 0) return 0.0f;
        float secondsPlayed = alGetSourcef(sourceId, AL_SEC_OFFSET);
        checkErrors("alGetSourcef");
        float totalDuration = totalSamples / (float) sampleRate;
        if (totalDuration <= 0f) return 0.0f;
        return Math.min(secondsPlayed / totalDuration, 1.0f);
    }

    public void cleanup() {
        if (sourceId != -1) {
            alSourceStop(sourceId);
            alDeleteSources(sourceId);
            sourceId = -1;
        }
        if (bufferId != -1) {
            alDeleteBuffers(bufferId);
            bufferId = -1;
        }

        loaded = false;
    }
}

