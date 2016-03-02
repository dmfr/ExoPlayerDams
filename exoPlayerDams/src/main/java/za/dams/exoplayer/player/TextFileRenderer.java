package za.dams.exoplayer.player;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.os.Handler.Callback;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.Subtitle;
import com.google.android.exoplayer.text.SubtitleParser;
import com.google.android.exoplayer.text.TextRenderer;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.Util;

public class TextFileRenderer extends TrackRenderer implements Callback {
	
	private static final class PlayableSubtitle implements Subtitle {

		  /**
		   * The start time of the subtitle.
		   * <p>
		   * May be less than {@code getEventTime(0)}, since a subtitle may begin prior to the time of the
		   * first event.
		   */
		  public final long startTimeUs;

		  private final Subtitle subtitle;
		  private final long offsetUs;

		  /**
		   * @param subtitle The subtitle to wrap.
		   * @param isRelative True if the wrapped subtitle's timestamps are relative to the start time.
		   *     False if they are absolute.
		   * @param startTimeUs The start time of the subtitle.
		   * @param offsetUs An offset to add to the subtitle timestamps.
		   */
		  public PlayableSubtitle(Subtitle subtitle, boolean isRelative, long startTimeUs, long offsetUs) {
		    this.subtitle = subtitle;
		    this.startTimeUs = startTimeUs;
		    this.offsetUs = (isRelative ? startTimeUs : 0) + offsetUs;
		  }

		  @Override
		  public int getEventTimeCount() {
		    return subtitle.getEventTimeCount();
		  }

		  @Override
		  public long getEventTime(int index) {
		    return subtitle.getEventTime(index) + offsetUs;
		  }

		  @Override
		  public long getLastEventTime() {
		    return subtitle.getLastEventTime() + offsetUs;
		  }

		  @Override
		  public int getNextEventTimeIndex(long timeUs) {
		    return subtitle.getNextEventTimeIndex(timeUs - offsetUs);
		  }

		  @Override
		  public List<Cue> getCues(long timeUs) {
		    return subtitle.getCues(timeUs - offsetUs);
		  }

		}
	private static final class SubtitleParserHelper implements Handler.Callback {
		  private static final int MSG_FORMAT = 0;
		  private static final int MSG_SAMPLE = 1;

		  private final SubtitleParser parser;
		  private final Handler handler;

		  private SampleHolder sampleHolder;
		  private boolean parsing;
		  private PlayableSubtitle result;
		  private IOException error;
		  private RuntimeException runtimeError;

		  private boolean subtitlesAreRelative;
		  private long subtitleOffsetUs;

		  /**
		   * @param looper The {@link Looper} associated with the thread on which parsing should occur.
		   * @param parser The parser that should be used to parse the raw data.
		   */
		  public SubtitleParserHelper(Looper looper, SubtitleParser parser) {
		    this.handler = new Handler(looper, this);
		    this.parser = parser;
		    flush();
		  }

		  /**
		   * Flushes the helper, canceling the current parsing operation, if there is one.
		   */
		  public synchronized void flush() {
		    sampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_NORMAL);
		    parsing = false;
		    result = null;
		    error = null;
		    runtimeError = null;
		  }

		  /**
		   * Whether the helper is currently performing a parsing operation.
		   *
		   * @return True if the helper is currently performing a parsing operation. False otherwise.
		   */
		  public synchronized boolean isParsing() {
		    return parsing;
		  }

		  /**
		   * Gets the holder that should be populated with data to be parsed.
		   * <p>
		   * The returned holder will remain valid unless {@link #flush()} is called. If {@link #flush()}
		   * is called the holder is replaced, and this method should be called again to obtain the new
		   * holder.
		   *
		   * @return The holder that should be populated with data to be parsed.
		   */
		  public synchronized SampleHolder getSampleHolder() {
		    return sampleHolder;
		  }

		  /**
		   * Sets the format of subsequent samples.
		   *
		   * @param format The format.
		   */
		  public void setFormat(MediaFormat format) {
		    handler.obtainMessage(MSG_FORMAT, format).sendToTarget();
		  }

		  /**
		   * Start a parsing operation.
		   * <p>
		   * The holder returned by {@link #getSampleHolder()} should be populated with the data to be
		   * parsed prior to calling this method.
		   */
		  public synchronized void startParseOperation() {
		    Assertions.checkState(!parsing);
		    parsing = true;
		    result = null;
		    error = null;
		    runtimeError = null;
		    handler.obtainMessage(MSG_SAMPLE, Util.getTopInt(sampleHolder.timeUs),
		        Util.getBottomInt(sampleHolder.timeUs), sampleHolder).sendToTarget();
		  }

		  /**
		   * Gets the result of the most recent parsing operation.
		   * <p>
		   * The result is cleared as a result of calling this method, and so subsequent calls will return
		   * null until a subsequent parsing operation has finished.
		   *
		   * @return The result of the parsing operation, or null.
		   * @throws IOException If the parsing operation failed.
		   */
		  public synchronized PlayableSubtitle getAndClearResult() throws IOException {
		    try {
		      if (error != null) {
		        throw error;
		      } else if (runtimeError != null) {
		        throw runtimeError;
		      } else {
		        return result;
		      }
		    } finally {
		      result = null;
		      error = null;
		      runtimeError = null;
		    }
		  }

		  @Override
		  public boolean handleMessage(Message msg) {
		    switch (msg.what) {
		      case MSG_FORMAT:
		        handleFormat((MediaFormat) msg.obj);
		        break;
		      case MSG_SAMPLE:
		        long sampleTimeUs = Util.getLong(msg.arg1, msg.arg2);
		        SampleHolder holder = (SampleHolder) msg.obj;
		        handleSample(sampleTimeUs, holder);
		        break;
		    }
		    return true;
		  }

		  private void handleFormat(MediaFormat format) {
		    subtitlesAreRelative = format.subsampleOffsetUs == MediaFormat.OFFSET_SAMPLE_RELATIVE;
		    subtitleOffsetUs = subtitlesAreRelative ? 0 : format.subsampleOffsetUs;
		  }

		  private void handleSample(long sampleTimeUs, SampleHolder holder) {
		    Subtitle parsedSubtitle = null;
		    IOException error = null;
		    RuntimeException runtimeError = null;
		    try {
		      parsedSubtitle = parser.parse(holder.data.array(), 0, holder.size);
		    } catch (IOException e) {
		      error = e;
		    } catch (RuntimeException e) {
		      runtimeError = e;
		    }
		    synchronized (this) {
		      if (sampleHolder != holder) {
		        // A flush has occurred since this holder was posted. Do nothing.
		      } else {
		        this.result = new PlayableSubtitle(parsedSubtitle, subtitlesAreRelative, sampleTimeUs,
		            subtitleOffsetUs);
		        this.error = error;
		        this.runtimeError = runtimeError;
		        this.parsing = false;
		      }
		    }
		  }
		
	}
	
	private static final int MSG_UPDATE_OVERLAY = 0;
	
	private static final List<Class<? extends SubtitleParser>> DEFAULT_PARSER_CLASSES;
	static {
		DEFAULT_PARSER_CLASSES = new ArrayList<>();
		// Load parsers using reflection so that they can be deleted cleanly.
		// Class.forName(<class name>) appears for each parser so that automated tools like proguard
		// can detect the use of reflection (see http://proguard.sourceforge.net/FAQ.html#forname).
		try {
			DEFAULT_PARSER_CLASSES.add(
					Class.forName("com.google.android.exoplayer.text.webvtt.WebvttParser")
					.asSubclass(SubtitleParser.class));
		} catch (ClassNotFoundException e) {
			// Parser not found.
		}
		try {
			DEFAULT_PARSER_CLASSES.add(
					Class.forName("com.google.android.exoplayer.text.ttml.TtmlParser")
					.asSubclass(SubtitleParser.class));
		} catch (ClassNotFoundException e) {
			// Parser not found.
		}
		try {
			DEFAULT_PARSER_CLASSES.add(
					Class.forName("com.google.android.exoplayer.text.subrip.SubripParser")
					.asSubclass(SubtitleParser.class));
		} catch (ClassNotFoundException e) {
			// Parser not found.
		}
		try {
			DEFAULT_PARSER_CLASSES.add(
					Class.forName("com.google.android.exoplayer.text.tx3g.Tx3gParser")
					.asSubclass(SubtitleParser.class));
		} catch (ClassNotFoundException e) {
			// Parser not found.
		}
	}
	  
	private final Handler textRendererHandler;
	private final TextRenderer textRenderer;
	private final MediaFormatHolder formatHolder;
	private final SubtitleParser[] subtitleParsers;

	private int parserIndex;
	private boolean inputStreamEnded;
	private PlayableSubtitle subtitle;
	private PlayableSubtitle nextSubtitle;
	private SubtitleParserHelper parserHelper;
	private HandlerThread parserThread;
	private int nextSubtitleEventIndex;
	
	
	private int lastModulo ;
	  
	public TextFileRenderer(TextRenderer textRenderer,
		      Looper textRendererLooper, SubtitleParser... subtitleParsers) {
		super() ;
		
	    this.textRenderer = Assertions.checkNotNull(textRenderer);
	    this.textRendererHandler = textRendererLooper == null ? null
	        : new Handler(textRendererLooper, this);
	    if (subtitleParsers == null || subtitleParsers.length == 0) {
	      subtitleParsers = new SubtitleParser[DEFAULT_PARSER_CLASSES.size()];
	      for (int i = 0; i < subtitleParsers.length; i++) {
	        try {
	          subtitleParsers[i] = DEFAULT_PARSER_CLASSES.get(i).newInstance();
	        } catch (InstantiationException e) {
	          throw new IllegalStateException("Unexpected error creating default parser", e);
	        } catch (IllegalAccessException e) {
	          throw new IllegalStateException("Unexpected error creating default parser", e);
	        }
	      }
	    }
	    this.subtitleParsers = subtitleParsers;
	    formatHolder = new MediaFormatHolder();
		
	}


	@Override
	protected boolean doPrepare(long positionUs) throws ExoPlaybackException {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	protected int getTrackCount() {
		// TODO Auto-generated method stub
		return 1;
	}

	@Override
	protected MediaFormat getFormat(int track) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected boolean isEnded() {
		// TODO 
		return false;
	}

	@Override
	protected boolean isReady() {
		return true;
	}

	@Override
	protected void doSomeWork(long positionUs, long elapsedRealtimeUs)
			throws ExoPlaybackException {
	    if (getState() != TrackRenderer.STATE_STARTED) {
	    	// Log.w("DAMS","Not started");
	        return;
	      }
		
		boolean needsUpdate = false;
		int positionS = (int)(positionUs / (1000*1000)) ;
		if( positionS % 2 != lastModulo ) {
			needsUpdate = true ;
			lastModulo = (int)(positionS % 2) ;
		}
		
		if( !needsUpdate ) {
			return ;
		}
		
		Log.w("DAMS","updating..."+lastModulo) ;
		
		Cue testCue = new Cue((lastModulo>0 ? "TEST TEST":"NO TEST")) ;
		List<Cue> testCueList = new ArrayList<Cue>() ;
		testCueList.add(testCue) ;
		updateTextRenderer(testCueList);
	}

	@Override
	protected void maybeThrowError() throws ExoPlaybackException {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected long getDurationUs() {
		return MATCH_LONGEST_US ;
	}

	@Override
	protected long getBufferedPositionUs() {
		return END_OF_TRACK_US;
	}

	@Override
	protected void seekTo(long positionUs) throws ExoPlaybackException {
		return ;
	}

	
	private void updateTextRenderer(List<Cue> cues) {
		if (textRendererHandler != null) {
			textRendererHandler.obtainMessage(MSG_UPDATE_OVERLAY, cues).sendToTarget();
		} else {
			invokeRendererInternalCues(cues);
		}
	}

	private void clearTextRenderer() {
		updateTextRenderer(Collections.<Cue>emptyList());
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean handleMessage(Message msg) {
		switch (msg.what) {
		case MSG_UPDATE_OVERLAY:
			invokeRendererInternalCues((List<Cue>) msg.obj);
			return true;
		}
		return false;
	}

	private void invokeRendererInternalCues(List<Cue> cues) {
		textRenderer.onCues(cues);
	}
	
}
