package com.x1unix.avi;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.LoopingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveVideoTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.x1unix.moonwalker.Listener;
import com.x1unix.moonwalker.ManifestCollection;
import com.x1unix.moonwalker.MoonSession;
import com.x1unix.moonwalker.MoonVideo;
import com.x1unix.moonwalker.Moonwalker;

import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import okhttp3.OkHttpClient;

public class PlayerActivity extends AppCompatActivity {

    // Root view components
    @BindView(R.id.player) SimpleExoPlayerView playerView;
    @BindView(R.id.player_loading) ProgressBar preloader;
    @BindView(R.id.player_ui) RelativeLayout ui;

    // First header elements
    @BindView(R.id.btn_back) ImageView btnBack;
    @BindView(R.id.player_title) TextView title;
    @BindView(R.id.player_subtitle) TextView subtitle;

    // Menu
    @BindView(R.id.btn_quality) ImageView btnQuality;
    @BindView(R.id.btn_share) ImageView btnShare;
    @BindView(R.id.btn_select) ImageView btnSelect;

    // Player controls
    @BindView(R.id.player_play) ImageView btnPlay;
    @BindView(R.id.player_pause) ImageView btnPause;

    // Bottom controls
    @BindView(R.id.time_current) TextView timeCurrentLabel;
    @BindView(R.id.time_total) TextView timeTotalLabel;
    @BindView(R.id.player_seekbar) SeekBar seekBar;

    private String kpId;
    private MoonVideo currentVideo;
    private MoonSession currentSession;

    private static final String TAG = "AviPlayer";
    public static final String ARG_KPID = "kpId";
    public static final String ARG_TITLE = "title";
    public static final String ARG_DESCRIPTION = "description";

    private SimpleExoPlayer player;
    private ExoPlayer.EventListener exoPlayerEventListener;

    private LoopingMediaSource loopingSource;
    private boolean isPlayingNow = false;
    private boolean isDragging = false;
    private Handler updateHandler = new Handler();

    private int resumeWindow;
    private long resumePosition;

    private Moonwalker moonwalker = new Moonwalker("http://avi.x1unix.com/");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        ButterKnife.bind(this);
        Intent i = getIntent();
        playerView.setUseController(false);

        try {
            initializeActivity(i);
        } catch (Exception ex) {
            Toast.makeText(this, "Failed to start player, please try again later", Toast.LENGTH_LONG)
                    .show();
        }

    }

    private void initializeActivity(Intent i) {
        title.setText(i.getStringExtra(ARG_TITLE));
        this.kpId = i.getStringExtra(ARG_KPID);

        moonwalker.getMovieByKinopoiskId(this.kpId,
                new Listener() {
                    @Override
                    public void onSuccess(final MoonVideo video, final OkHttpClient client) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    onDataReady(video, client);
                                } catch (Exception ex) {
                                    Log.e(TAG, "[" + ex.getClass() +"]: " + ex.getMessage());
                                    panic("Не удалось извлечь данные для вопроизведения");
                                }
                            }
                        });
                    }

                    @Override
                    public void onError(Exception exception) {
                        final Exception ex = exception;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.e(TAG, "[" + ex.getClass() +"]: " + ex.getMessage());
                                panic("Запрошеное видео не найдено или заблокировано в вашем регионе.");
                            }
                        });
                    }
                }
        );

    }

    private void onDataReady(MoonVideo video, OkHttpClient client) {
        currentVideo = video;
        currentSession = video.getSession();
        ManifestCollection playlist = video.getPlaylist();

        // Thanks @ayalus for example (ExoPlayer-2-Example)

        // 1. Create a default TrackSelector
        Handler mainHandler = new Handler();
        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveVideoTrackSelection.Factory(bandwidthMeter);
        TrackSelector trackSelector = new DefaultTrackSelector(mainHandler, videoTrackSelectionFactory);

        // 2. Create a default LoadControl
        LoadControl loadControl = new DefaultLoadControl();

        // 3. Create the player
        player = ExoPlayerFactory.newSimpleInstance(this, trackSelector, loadControl);

        //Set media controller
        playerView.requestFocus();

        // Bind the player to the view.
        playerView.setPlayer(player);

        // Livestream url
        Uri m3uStreamUri = Uri.parse(playlist.getM3u8Manifest());

        // Measures bandwidth during playback. Can be null if not required.
        DefaultBandwidthMeter bandwidthMeterA = new DefaultBandwidthMeter();

        // Produces DataSource instances through which media data is loaded.
        // DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this, Util.getUserAgent(this, "exoplayer2example"), bandwidthMeterA);
        String userAgent = currentSession.getUserAgent();

        DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(this, Util.getUserAgent(this, userAgent), bandwidthMeterA);


        //Produces Extractor instances for parsing the media data.
        ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();

        //FOR LIVESTREAM LINK:
        MediaSource videoSource = new HlsMediaSource(m3uStreamUri, dataSourceFactory, 1, null, null);
        loopingSource = new LoopingMediaSource(videoSource);

        // Prepare the player with the source.
        player.prepare(loopingSource);


        player.addListener(videoEventListener);

        player.setPlayWhenReady(true);

        setUIVisibility(true);

    }

    private ExoPlayer.EventListener videoEventListener = new ExoPlayer.EventListener() {
        @Override
        public void onLoadingChanged(boolean isLoading) {
            int visibility = (isLoading) ? View.VISIBLE : View.GONE;
            preloader.setVisibility(visibility);
            Log.v(TAG,"Listener-onLoadingChanged...");

        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            Log.v(TAG,"Listener-onPlayerStateChanged...");

            checkProgressUpdate();

            switch (playbackState) {
                case ExoPlayer.STATE_BUFFERING:
                    preloader.setVisibility(View.VISIBLE);
                    break;
                case ExoPlayer.STATE_READY:
                    preloader.setVisibility(View.GONE);

                    long realDurationMillis = player.getDuration();
                    setDurationTime(realDurationMillis);

                    break;
            }

        }

        @Override
        public void onTimelineChanged(Timeline timeline, Object manifest) {
            Log.v(TAG,"Listener-onTimelineChanged...");

        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
            Log.v(TAG,"Listener-onPlayerError...");
            player.stop();
            player.prepare(loopingSource);
            player.setPlayWhenReady(true);
        }

        @Override
        public void onPositionDiscontinuity() {
            Log.v(TAG,"Listener-onPositionDiscontinuity...");

        }
    };

    private String getTime(long millis) {
        Long ms = TimeUnit.MILLISECONDS.toSeconds(millis);
        Integer seconds = ms.intValue();
        String time = String.format("%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(millis),
                TimeUnit.MILLISECONDS.toSeconds(millis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
        );

        return time;
    }

    private void setDurationTime(long millis) {
        String time = getTime(millis);
        int secs = getPlayedSeconds(millis);

        timeTotalLabel.setText(time);
        seekBar.refreshDrawableState();
        seekBar.setMax(secs);
    }

    private Integer getPlayedSeconds(long millis) {
        Long ms = TimeUnit.MILLISECONDS.toSeconds(millis);
        Integer seconds = ms.intValue();
        ms = null;

        return seconds;
    }

    public void updateProgress(long total, long current, long buffered) {
        final String totalTime = getTime(total);
        final String currentTime = getTime(current);
        final int totalSec = getPlayedSeconds(total);
        final int currentSec = getPlayedSeconds(current);
        final int bufferedSec = getPlayedSeconds(buffered);


        Log.d(TAG, "========= TOTAL:" + totalTime + "; CURRENT: " + currentTime +" =========");

        //if (!isDragging) {
        seekBar.setProgress(currentSec);
        //}

        seekBar.setMax(totalSec);
        seekBar.setSecondaryProgress(bufferedSec);
        timeTotalLabel.setText(totalTime);
        timeCurrentLabel.setText(currentTime);

        timeTotalLabel.invalidate();
        timeCurrentLabel.invalidate();
        seekBar.invalidate();
    }

    private void checkProgressUpdate() {
        long duration = player == null ? 0 : player.getDuration();
        long position = player == null ? 0 : player.getCurrentPosition();
        long bufferedPosition = player == null ? 0 : player.getBufferedPosition();

        updateProgress(duration, position, bufferedPosition);

        // Remove scheduled updates.
        updateHandler.removeCallbacks(updateProgressAction);
        // Schedule an update if necessary.
        int playbackState = player == null ? ExoPlayer.STATE_IDLE : player.getPlaybackState();
        if (playbackState != ExoPlayer.STATE_IDLE && playbackState != ExoPlayer.STATE_ENDED) {
            long delayMs;
            if (player.getPlayWhenReady() && playbackState == ExoPlayer.STATE_READY) {
                delayMs = 1000 - (position % 1000);
                if (delayMs < 200) {
                    delayMs += 1000;
                }
            } else {
                delayMs = 1000;
            }
            updateHandler.postDelayed(updateProgressAction, delayMs);
        }
    }

    private final Runnable updateProgressAction = new Runnable() {
        @Override
        public void run() {
            checkProgressUpdate();
        }
    };

    public void setUIVisibility(boolean isVisible) {
        ui.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    @OnClick(R.id.player_play)
    public void onPlayBtnClick() {
        btnPlay.setVisibility(View.GONE);
        btnPause.setVisibility(View.VISIBLE);
    }

    @OnClick(R.id.player_pause)
    public void onPauseBtnClick() {
        btnPlay.setVisibility(View.VISIBLE);
        btnPause.setVisibility(View.GONE);
    }

    private void panic(String err) {
        AlertDialog.Builder builder = new AlertDialog.Builder(PlayerActivity.this);
        builder
                .setTitle("Ошибка")
                .setMessage(err)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        finish();
                    }
                })
                .create()
                .show();
    }

    @OnClick(R.id.btn_back)
    public void goBack() {
        finish();
    }

    @Override
    protected void onStop() {
        if (player != null) player.setPlayWhenReady(false);
        super.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        if (player != null) player.setPlayWhenReady(true);
        super.onResume();
    }

    @Override
    protected void onPause() {
        if (player != null) player.setPlayWhenReady(false);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (player != null) player.release();
        super.onDestroy();

    }


}
