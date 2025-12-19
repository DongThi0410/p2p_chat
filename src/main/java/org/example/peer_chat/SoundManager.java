package org.example.peer_chat;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * Manages sound notifications programmatically (beep, ringtone)
 * without external audio files.
 */
public class SoundManager {
    // Singleton or static usage
    private static SoundManager instance;
    private volatile boolean ringing = false;
    private Thread ringThread;

    public static synchronized SoundManager getInstance() {
        if (instance == null) {
            instance = new SoundManager();
        }
        return instance;
    }

    /**
     * Play a short beep for new messages.
     */
    public void playMessageSound() {
        new Thread(() -> {
            try {
                generateTone(800, 150, 0.5); // 800Hz, 150ms, low volume
            } catch (Exception e) {
                System.err.println("[Sound] Msg tone error: " + e.getMessage());
            }
        }, "sound-msg").start();
    }

    /**
     * Start a looping ringtone for incoming calls.
     */
    public synchronized void playIncomingCallSound() {
        if (ringing) return; // already ringing
        ringing = true;

        ringThread = new Thread(() -> {
            while (ringing) {
                try {
                    // Play a sequence: Ring... Ring...
                    generateTone(700, 600, 0.8);
                    Thread.sleep(100);
                    generateTone(700, 900, 0.8);
                    
                    // pause between rings
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    break;
                } catch (LineUnavailableException e) {
                    System.err.println("[Sound] Ring error: " + e.getMessage());
                    break;
                }
            }
        }, "sound-ring");
        ringThread.start();
    }

    /**
     * Stop the ringtone.
     */
    public synchronized void stopIncomingCallSound() {
        ringing = false;
        if (ringThread != null) {
            ringThread.interrupt();
            ringThread = null;
        }
    }

    /**
     * Generates a sine wave tone on the default audio line.
     * @param hz Frequency
     * @param msecs Duration in milliseconds
     * @param vol Volume 0.0 to 1.0
     */
    private void generateTone(int hz, int msecs, double vol) throws LineUnavailableException {
        float sampleRate = 8000f;
        byte[] buf = new byte[1];
        AudioFormat af = new AudioFormat(
                sampleRate, // sampleRate
                8,           // sampleSizeInBits
                1,           // channels
                true,        // signed
                false);      // bigEndian
        
        try (SourceDataLine sdl = AudioSystem.getSourceDataLine(af)) {
            sdl.open(af);
            sdl.start();
            
            for (int i = 0; i < msecs * 8; i++) { // 8000 samples/sec -> 8 samples/ms
                double angle = i / (sampleRate / hz) * 2.0 * Math.PI;
                buf[0] = (byte) (Math.sin(angle) * 127.0 * vol);
                sdl.write(buf, 0, 1);
            }
            sdl.drain();
            sdl.stop();
        }
    }
}
