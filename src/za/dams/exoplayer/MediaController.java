package za.dams.exoplayer;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.util.PlayerControl;

import android.content.Context;
import android.os.Handler;

public class MediaController extends android.widget.MediaController {
	public MediaController(Context context) {
		super(context);
	}

	public void setMediaPlayer(MediaPlayerControl player) {
		// TODO Auto-generated method stub
		super.setMediaPlayer(player);
	}

	public static class MediaPlayerControl extends PlayerControl implements ExoPlayer.Listener{
		private int mPlayerState ;
		private boolean mSeekPending ;
		private int mSeekToMillis = -1 ;
		
		private Handler mHandler ;
		private Runnable mDoSeekRunnable ;

		public MediaPlayerControl(ExoPlayer exoPlayer) {
			super(exoPlayer);
			
			exoPlayer.addListener(this);
			
			mHandler = new Handler() ;
			mDoSeekRunnable = new Runnable(){
				@Override
				public void run() {
					MediaPlayerControl.this.doSeek() ;
				}
			};
		}
		
		@Override
		public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
			// TODO Auto-generated method stub
			mPlayerState = playbackState ;
		}

		@Override
		public void onPlayWhenReadyCommitted() {
			// TODO Auto-generated method stub
		}

		@Override
		public void onPlayerError(ExoPlaybackException error) {
			// TODO Auto-generated method stub
		}

		@Override
		public void seekTo(int timeMillis) {
			mSeekPending = true ;
			mSeekToMillis = timeMillis ;
			
			int seekDelay = (mSeekPending ? 500:100) ;
			mHandler.removeCallbacks(mDoSeekRunnable);
			mHandler.postDelayed(mDoSeekRunnable, seekDelay);
		}
		private void doSeek() {
			super.seekTo(mSeekToMillis);
			mSeekPending = false ;
		}

		@Override
		public int getCurrentPosition() {
			if( mSeekPending ) {
				return mSeekToMillis ;
			}
			return super.getCurrentPosition();
		}
		
		
	}
}
