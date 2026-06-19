package com.yoyo.jingxi.network;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes compressed audio (AAC/MPEG-4 from MediaRecorder) to 16kHz mono PCM
 * for input to sherpa-onnx OfflineRecognizer.
 */
public class SherpaResampler {

    /**
     * Decode an audio file to 16kHz mono 16-bit PCM samples.
     * @return float array normalized to [-1.0, 1.0] at 16000 Hz sample rate
     */
    public static float[] decodeToPcm(File audioFile, int targetSampleRate) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(audioFile.getAbsolutePath());

        int audioTrackIndex = -1;
        MediaFormat trackFormat = null;

        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                audioTrackIndex = i;
                trackFormat = format;
                break;
            }
        }

        if (audioTrackIndex < 0) {
            extractor.release();
            throw new IOException("找不到音频轨道");
        }

        extractor.selectTrack(audioTrackIndex);

        String mime = trackFormat.getString(MediaFormat.KEY_MIME);
        int sourceSampleRate = trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                ? trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                : 44100;
        int channelCount = trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                ? trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                : 1;

        MediaCodec decoder = MediaCodec.createDecoderByType(mime);
        decoder.configure(trackFormat, null, null, 0);
        decoder.start();

        // Collect all decoded PCM short[]
        List<short[]> decodedChunks = new ArrayList<>();
        int totalSamples = 0;
        boolean inputDone = false;
        boolean outputDone = false;

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        while (!outputDone) {
            if (!inputDone) {
                int inIndex = decoder.dequeueInputBuffer(10000);
                if (inIndex >= 0) {
                    ByteBuffer inputBuffer = decoder.getInputBuffer(inIndex);
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        long presentationTime = extractor.getSampleTime();
                        decoder.queueInputBuffer(inIndex, 0, sampleSize, presentationTime, 0);
                        extractor.advance();
                    }
                }
            }

            int outIndex = decoder.dequeueOutputBuffer(info, 10000);
            if (outIndex >= 0) {
                ByteBuffer outputBuffer = decoder.getOutputBuffer(outIndex);
                ShortBuffer shortBuffer = outputBuffer.asShortBuffer();
                short[] chunk = new short[info.size / 2];
                shortBuffer.get(chunk);
                decodedChunks.add(chunk);
                totalSamples += chunk.length;
                decoder.releaseOutputBuffer(outIndex, false);

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    outputDone = true;
                }
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Format changed - we can ignore since we already know the format
            }
        }

        decoder.stop();
        decoder.release();
        extractor.release();

        // Merge chunks and resample
        short[] mergedPcm = new short[totalSamples];
        int offset = 0;
        for (short[] chunk : decodedChunks) {
            System.arraycopy(chunk, 0, mergedPcm, offset, chunk.length);
            offset += chunk.length;
        }

        return resample(mergedPcm, sourceSampleRate, channelCount, targetSampleRate);
    }

    /**
     * Resample and convert to mono 16kHz float samples.
     */
    private static float[] resample(short[] input, int sourceRate, int channels, int targetRate) {
        // First convert to mono if needed
        short[] monoSamples;
        if (channels > 1) {
            monoSamples = new short[input.length / channels];
            for (int i = 0; i < monoSamples.length; i++) {
                int sum = 0;
                for (int ch = 0; ch < channels; ch++) {
                    sum += input[i * channels + ch];
                }
                monoSamples[i] = (short) (sum / channels);
            }
        } else {
            monoSamples = input;
        }

        // Simple linear resampling
        double ratio = (double) sourceRate / targetRate;
        int outputLength = (int) (monoSamples.length / ratio);
        float[] output = new float[outputLength];

        for (int i = 0; i < outputLength; i++) {
            double srcIndex = i * ratio;
            int idx0 = (int) srcIndex;
            int idx1 = Math.min(idx0 + 1, monoSamples.length - 1);
            double frac = srcIndex - idx0;

            float s0 = monoSamples[idx0] / 32768.0f;
            float s1 = monoSamples[idx1] / 32768.0f;
            output[i] = s0 + (float) (frac * (s1 - s0));
        }

        return output;
    }
}
