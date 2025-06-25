package me.jtech.packified.client.util;

import me.jtech.packified.Packified;
import net.minecraft.client.sound.AlUtil;
import net.minecraft.client.sound.Source;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL11.AL_SEC_OFFSET;
import static org.lwjgl.system.MemoryUtil.NULL;

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
        this.sampleRate = sampleRate;
        this.totalSamples = waveform.length / channels;

        // Create OpenAL buffer and source
        bufferId = alGenBuffers();
        sourceId = alGenSources();

        // Convert float array to PCM16 buffer
        java.nio.ShortBuffer pcmBuffer = BufferUtils.createShortBuffer(waveform.length);
        for (float sample : waveform) {
            pcmBuffer.put((short) (sample * 32767));
        }
        pcmBuffer.flip();

        // Determine OpenAL format
        int format = (channels == 1) ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;

        // Upload to OpenAL buffer
        alBufferData(bufferId, format, pcmBuffer, sampleRate);

        // Attach buffer to source
        alSourcei(sourceId, AL_BUFFER, bufferId);

        loaded = true;
    }

    public void play() {
        if (sourceId != -1) {
            alSourcePlay(sourceId);
        }
    }

    public void pause() {
        if (sourceId != -1) {
            alSourcePause(sourceId);
        }
    }

    public void stop() {
        if (sourceId != -1) {
            alSourceStop(sourceId);
        }
    }

    public boolean isPlaying() {
        if (sourceId == -1) return false;
        int state = alGetSourcei(sourceId, AL_SOURCE_STATE);
        return state == AL_PLAYING;
    }

    public float getPlayheadPosition() {
        if (sourceId == -1) return 0.0f;
        float secondsPlayed = alGetSourcef(sourceId, AL_SEC_OFFSET);
        float totalDuration = totalSamples / (float) sampleRate;
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

