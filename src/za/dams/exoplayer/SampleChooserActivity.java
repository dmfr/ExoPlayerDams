/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package za.dams.exoplayer;

import za.dams.exoplayer.Samples.Sample;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

/**
 * An activity for selecting from a number of samples.
 */
public class SampleChooserActivity extends Activity {
	
	private static final int CHOOSE_FILE_REQUESTCODE = 1 ;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sample_chooser_activity);

    ListView sampleList = (ListView) findViewById(R.id.sample_list);
    final SampleAdapter sampleAdapter = new SampleAdapter(this);

    sampleAdapter.add(new Header("Manual selection"));
    sampleAdapter.add(new Chooser(Chooser.TYPE_INPUT));
    sampleAdapter.add(new Chooser(Chooser.TYPE_PICKER));
    sampleAdapter.add(new Header("YouTube DASH"));
    sampleAdapter.addAll((Object[]) Samples.YOUTUBE_DASH_MP4);
    sampleAdapter.addAll((Object[]) Samples.YOUTUBE_DASH_WEBM);
    sampleAdapter.add(new Header("Widevine DASH Policy Tests (GTS)"));
    sampleAdapter.addAll((Object[]) Samples.WIDEVINE_GTS);
    sampleAdapter.add(new Header("Widevine HDCP Capabilities Tests"));
    sampleAdapter.addAll((Object[]) Samples.WIDEVINE_HDCP);
    sampleAdapter.add(new Header("Widevine DASH: MP4,H264"));
    sampleAdapter.addAll((Object[]) Samples.WIDEVINE_H264_MP4_CLEAR);
    sampleAdapter.addAll((Object[]) Samples.WIDEVINE_H264_MP4_SECURE);
    sampleAdapter.add(new Header("Widevine DASH: WebM,VP9"));
    sampleAdapter.addAll((Object[]) Samples.WIDEVINE_VP9_WEBM_CLEAR);
    sampleAdapter.addAll((Object[]) Samples.WIDEVINE_VP9_WEBM_SECURE);
    sampleAdapter.add(new Header("Widevine DASH: MP4,H265"));
    sampleAdapter.addAll((Object[]) Samples.WIDEVINE_H265_MP4_CLEAR);
    sampleAdapter.addAll((Object[]) Samples.WIDEVINE_H265_MP4_SECURE);
    sampleAdapter.add(new Header("SmoothStreaming"));
    sampleAdapter.addAll((Object[]) Samples.SMOOTHSTREAMING);
    sampleAdapter.add(new Header("HLS"));
    sampleAdapter.addAll((Object[]) Samples.HLS);
    sampleAdapter.add(new Header("Misc"));
    sampleAdapter.addAll((Object[]) Samples.MISC);

    sampleList.setAdapter(sampleAdapter);
    sampleList.setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Object item = sampleAdapter.getItem(position);
        if (item instanceof Sample) {
            onSampleSelected((Sample) item);
          }
        if (item instanceof Chooser) {
        	switch( ((Chooser)item).type ) {
        	case Chooser.TYPE_INPUT :
        		doOpenTextPicker() ;
        		break ;
        	case Chooser.TYPE_PICKER :
        		doOpenFilePicker() ;
        		break ;
        	default :
        		break ;
        	}
          }
      }
    });
  }

  private void onSampleSelected(Sample sample) {
    Intent mpdIntent = new Intent(this, PlayerActivity.class)
        .setData(Uri.parse(sample.uri))
        .putExtra(PlayerActivity.CONTENT_ID_EXTRA, sample.contentId)
        .putExtra(PlayerActivity.CONTENT_TYPE_EXTRA, sample.type)
        .putExtra(PlayerActivity.PROVIDER_EXTRA, sample.provider);
    startActivity(mpdIntent);
  }
  
  private void doOpenFilePicker() {
	  Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
	  intent.addCategory(Intent.CATEGORY_OPENABLE);
	  intent.setType("video/*");
	  Intent i = Intent.createChooser(intent, "File");
	  startActivityForResult(i,CHOOSE_FILE_REQUESTCODE);	  
  }
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	  String Fpath = data.getData().getPath();
	  // do somthing...
	  Intent mpdIntent = new Intent(this, PlayerActivity.class).setData(Uri.parse(Fpath)) ;
	  startActivity(mpdIntent);	     

	  super.onActivityResult(requestCode, resultCode, data);

  }
  
  
  private void doOpenTextPicker() {
	  final EditText txtUrl = new EditText(this);
	  txtUrl.setHint("Enter URL") ;
	  new AlertDialog.Builder(this)
	  .setTitle("Open URL")
	  .setView(txtUrl)
	  .setPositiveButton("OK", new DialogInterface.OnClickListener() {
	    public void onClick(DialogInterface dialog, int whichButton) {
	      String url = txtUrl.getText().toString();
		  Intent mpdIntent = new Intent(SampleChooserActivity.this, PlayerActivity.class).setData(Uri.parse(url)) ;
		  startActivity(mpdIntent);	     
	      dialog.dismiss();
	    }
	  })
	  .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
	    public void onClick(DialogInterface dialog, int whichButton) {
	    	dialog.dismiss();
	    }
	  })
	  .show();   }

  
  private static class SampleAdapter extends ArrayAdapter<Object> {

    public SampleAdapter(Context context) {
      super(context, 0);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      View view = convertView;
      if (view == null) {
        int layoutId = getItemViewType(position) > 0 ? android.R.layout.simple_list_item_1
            : R.layout.sample_chooser_inline_header;
        view = LayoutInflater.from(getContext()).inflate(layoutId, null, false);
      }
      Object item = getItem(position);
      String name = null;
      if (item instanceof Sample) {
        name = ((Sample) item).name;
      } else if (item instanceof Chooser) {
          name = ((Chooser) item).caption;
      } else if (item instanceof Header) {
          name = ((Header) item).name;
      }
      ((TextView) view).setText(name);
      return view;
    }

    @Override
    public int getItemViewType(int position) {
    	if( getItem(position) instanceof Chooser ) {
    		return 2 ;
    	} else if( getItem(position) instanceof Sample ) {
    		return 1 ;
    	} else {
    		return 0 ;
    	}
     
    }

    @Override
    public int getViewTypeCount() {
      return 3;
    }

  }

  private static class Header {

	    public final String name;

	    public Header(String name) {
	      this.name = name;
	    }

	  }

  private static class Chooser {

	    public final String caption ;
	    public final int type ;
	    public final static int TYPE_INPUT = 1 ;
	    public final static int TYPE_PICKER = 2 ;

	    public Chooser(int type) {
	    	this.type = type ;
	    	switch( type ) {
	    	case TYPE_INPUT :
	    		this.caption = "Enter URL" ;
	    		break ;
	    	case TYPE_PICKER :
	    		this.caption = "Select file" ;
	    		break ;
	    	default : 
	    		this.caption = "?" ;
	    		break ;
	    	}
	    }

	  }

}
