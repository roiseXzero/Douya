/*
 * Copyright (c) 2018 Zhang Hai <Dreaming.in.Code.ZH@Gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.douya.media;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import me.zhanghai.android.douya.R;
import me.zhanghai.android.douya.util.FunctionCompat;

public class MediaNotification {

    private Service mService;
    private MediaSessionCompat mMediaSession;
    private FunctionCompat.BooleanSupplier mIsPlaying;
    private String mChannelId;
    private int mChannelNameRes;
    private int mChannelDescriptionRes;
    private int mChannelImportance;
    private int mNotificationId;
    private int mSmallIconRes;
    private int mColorRes;

    private boolean mStarted;
    private boolean mChannelCreated;
    private boolean mForegroundStarted;

    private MediaControllerCompat.Callback mCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            postNotification();
        }
        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            postNotification();
        }
    };

    public MediaNotification(Service service, MediaSessionCompat mediaSession,
                             FunctionCompat.BooleanSupplier isPlaying, String channelId,
                             int channelNameRes, int channelDescriptionRes, int channelImportance,
                             int notificationId, int smallIconRes, int colorRes) {

        mService = service;
        mMediaSession = mediaSession;
        mIsPlaying = isPlaying;
        mChannelId = channelId;
        mChannelNameRes = channelNameRes;
        mChannelDescriptionRes = channelDescriptionRes;
        mChannelImportance = channelImportance;
        mNotificationId = notificationId;
        mSmallIconRes = smallIconRes;
        mColorRes = colorRes;
    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager) mService.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(mChannelId, mService.getString(
                mChannelNameRes), mChannelImportance);
        channel.setDescription(mService.getString(mChannelDescriptionRes));
        channel.setShowBadge(false);
        getNotificationManager().createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        MediaControllerCompat controller = mMediaSession.getController();
        MediaMetadataCompat metadata = controller.getMetadata();
        MediaDescriptionCompat description = metadata.getDescription();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mService, mChannelId)
                .setContentTitle(description.getTitle())
                .setContentText(description.getSubtitle())
                .setSubText(description.getDescription());
        if (description.getIconBitmap() != null) {
            builder.setLargeIcon(description.getIconBitmap());
        } else {
            // TODO
        }
        builder
                .setContentIntent(controller.getSessionActivity())
                .setDeleteIntent(MediaButtonReceiver.makePendingIntent(mService,
                        PlaybackStateCompat.ACTION_STOP))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(mSmallIconRes);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setColor(ContextCompat.getColor(mService, mColorRes));
        }
        builder.setShowWhen(false);
        boolean isPlaying = mIsPlaying.getAsBoolean();
        builder
                .setOngoing(isPlaying)
                .addAction(new NotificationCompat.Action(
                        R.drawable.skip_to_previous_icon_white_24dp, mService.getString(
                        R.string.media_action_skip_to_previous),
                        MediaButtonReceiver.makePendingIntent(mService,
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)))
                .addAction(new NotificationCompat.Action(isPlaying ?
                        R.drawable.pause_icon_white_24dp : R.drawable.play_icon_white_24dp,
                        mService.getString(isPlaying ? R.string.media_action_pause
                                : R.string.media_action_play),
                        MediaButtonReceiver.makePendingIntent(mService, isPlaying ?
                                PlaybackStateCompat.ACTION_PAUSE
                                : PlaybackStateCompat.ACTION_PLAY)))
                .addAction(new NotificationCompat.Action(
                        R.drawable.skip_to_next_icon_white_24dp, mService.getString(
                        R.string.media_action_skip_to_next),
                        MediaButtonReceiver.makePendingIntent(mService,
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT)))
                .setStyle(new android.support.v4.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mMediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2)
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(MediaButtonReceiver.makePendingIntent(mService,
                                PlaybackStateCompat.ACTION_STOP)));
        return builder.build();
    }

    private void postNotification() {
        if (!mChannelCreated) {
            createNotificationChannel();
            mChannelCreated = true;
        }
        boolean isPlaying = mIsPlaying.getAsBoolean();
        if (!isPlaying && mForegroundStarted) {
            mService.stopForeground(false);
            mForegroundStarted = false;
        }
        Notification notification = buildNotification();
        if (isPlaying) {
            mService.startForeground(mNotificationId, notification);
            mForegroundStarted = true;
        } else {
            getNotificationManager().notify(mNotificationId, notification);
        }
    }

    public void start() {
        if (mStarted) {
            return;
        }
        postNotification();
        mMediaSession.getController().registerCallback(mCallback);
        mStarted = true;
    }

    private void cancelNotification() {
        if (mForegroundStarted) {
            mService.stopForeground(true);
        } else {
            getNotificationManager().cancel(mNotificationId);
        }
    }

    public void stop() {
        mMediaSession.getController().unregisterCallback(mCallback);
        cancelNotification();
        mStarted = false;
    }
}
