package za.dams.exoplayer.player;

import android.content.Context;
import android.media.MediaCodec;
import android.net.Uri;

import com.google.android.exoplayer.FrameworkSampleSource;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import za.dams.exoplayer.player.DemoPlayer.RendererBuilder;
import za.dams.exoplayer.player.DemoPlayer.RendererBuilderCallback;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.text.TextTrackRenderer;
import com.google.android.exoplayer.text.tx3g.Tx3gParser;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;

public class DefaultRendererBuilder implements RendererBuilder {

	private final Context context;
	private final String userAgent;
	private final Uri uri;

	public DefaultRendererBuilder(Context context, String userAgent, Uri uri) {
		this.context = context;
		this.userAgent = userAgent;
		this.uri = uri;
	}

	@Override
	public void buildRenderers(DemoPlayer player, RendererBuilderCallback callback) {
		// Build the video and audio renderers.
		DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter(player.getMainHandler(),
				null);
		FrameworkSampleSource sampleSource = new FrameworkSampleSource(context, uri, null);
		MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(sampleSource,
				null, true, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000, null, player.getMainHandler(),
				player, 50);
		MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource,
				null, true, player.getMainHandler(), player);
		TrackRenderer textRenderer = new TextTrackRenderer(sampleSource, player,
				player.getMainHandler().getLooper(), new Tx3gParser());

		// Invoke the callback.
		TrackRenderer[] renderers = new TrackRenderer[DemoPlayer.RENDERER_COUNT];
		renderers[DemoPlayer.TYPE_VIDEO] = videoRenderer;
		renderers[DemoPlayer.TYPE_AUDIO] = audioRenderer;
		renderers[DemoPlayer.TYPE_TEXT] = textRenderer;
		callback.onRenderers(null, null, renderers, bandwidthMeter);
	}
}
