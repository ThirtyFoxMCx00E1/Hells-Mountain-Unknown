package com.hellsmountainunknown.game;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;

/**
 * Plays the studio/opening intro clip (assets/video/studio_intro.mp4) via
 * TextureView + MediaPlayer. Loops automatically since the clip itself is
 * only ~3s but the surrounding fade timeline in MainActivity runs longer
 * than that - looping means it just keeps playing under the fade rather
 * than freezing or going black partway through.
 *
 * Never allowed to block the intro sequence: any failure to load/prepare
 * the video calls the ready callback anyway so the fade timeline proceeds
 * (over a black screen) rather than getting stuck.
 */
public final class IntroVideoView extends TextureView implements TextureView.SurfaceTextureListener {

    public interface Listener {
        void onVideoReady();
    }

    private final Listener listener;
    private MediaPlayer player;
    private Surface videoSurface;
    private boolean released = false;
    private boolean readyCalled = false;

    public IntroVideoView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setSurfaceTextureListener(this);
    }

    private void notifyReadyOnce() {
        if (readyCalled) return;
        readyCalled = true;
        if (listener != null) listener.onVideoReady();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        try {
            videoSurface = new Surface(surfaceTexture);
            player = new MediaPlayer();
            AssetFileDescriptor afd = getContext().getAssets().openFd("video/studio_intro.mp4");
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            player.setSurface(videoSurface);
            player.setLooping(true);
            player.setOnPreparedListener(mp -> {
                try { mp.start(); } catch (Throwable ignored) {}
                notifyReadyOnce();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                notifyReadyOnce();
                return true;
            });
            player.prepareAsync();
        } catch (Throwable t) {
            android.util.Log.e("HellsMountainUnknown", "Intro video failed to load", t);
            notifyReadyOnce();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        release();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
    }

    public void release() {
        if (released) return;
        released = true;
        try {
            if (player != null) {
                player.stop();
                player.release();
                player = null;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (videoSurface != null) {
                videoSurface.release();
                videoSurface = null;
            }
        } catch (Throwable ignored) {
        }
    }
}
