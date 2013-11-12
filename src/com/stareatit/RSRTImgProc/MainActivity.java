//   Copyright 2013 Giancarlo Todone

//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at

//       http://www.apache.org/licenses/LICENSE-2.0

//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

//   for info: http://www.stareat.it/sp.aspx?g=f1af8b0a417242cdbb69ab26db9f69d1

package com.stareatit.RSRTImgProc;

import android.app.Activity;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.support.v8.renderscript.*;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import java.io.IOException;
import java.util.List;

public class MainActivity extends Activity implements SurfaceHolder.Callback
{
    private static final String TAG = "RSRTImgProcMain";
    
    Camera _camera = null;
    
    RenderScript _renderScript = null;
    ScriptC_filter _filtersLib = null;
    
    Allocation _inData = null;
    Allocation _outData = null;
    
    Bitmap _outBmp = null;
    ImageView _imageView = null;
    
    int _previewSamples = 0;
    
    SurfaceView _surfaceView;
    SurfaceHolder _holder;
    
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        setContentView(R.layout.main);
              
        _surfaceView = (SurfaceView)findViewById(R.id.surfaceView);
        _holder = _surfaceView.getHolder();
        _holder.addCallback(this);

        //deprecated in api level 11. Since here we need at least 14, it is no use.
        //mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        
        _surfaceView.setKeepScreenOn(true);
        
        _imageView =  (ImageView)findViewById(R.id.imageView);
        
        // renderscript stuff
        
        _renderScript = RenderScript.create(this); // <<The RenderScript context can be destroyed with destroy() or by allowing the RenderScript context object to be garbage collected.>>
        _filtersLib = new ScriptC_filter(_renderScript);
    }

    private void initStuff(int w, int h) // TODO: handle RGB, RGBA fotmats, too
    {
        _previewSamples = w * h;
        
        // init cam-preview buffers
        byte[] buf1 = new byte[_previewSamples*12/8]; // magic number pixel size for NV21/NV12/YV21
        byte[] buf2 = new byte[_previewSamples*12/8]; // assuming a YUV NV21 (should be default and available on all HW)
        byte[] buf3 = new byte[_previewSamples*12/8];
        
        // three buffers are more than enough!
        _camera.addCallbackBuffer(buf1);
        _camera.addCallbackBuffer(buf2);
        _camera.addCallbackBuffer(buf3);
        
        // init renderScriptStuffs
        Type.Builder tbIn = new Type.Builder(_renderScript, Element.U8(_renderScript));
        tbIn.setX(w);
        tbIn.setY(h);
        tbIn.setMipmaps(false);
        
        Type.Builder tbOut = new Type.Builder(_renderScript, Element.RGBA_8888(_renderScript));
        tbOut.setX(w); 
        tbOut.setY(h);
        tbOut.setMipmaps(false);
                
        _inData = Allocation.createTyped(_renderScript, tbIn.create(), Allocation.MipmapControl.MIPMAP_NONE,  Allocation.USAGE_SCRIPT & Allocation.USAGE_SHARED);
        _outData = Allocation.createTyped(_renderScript, tbOut.create(), Allocation.MipmapControl.MIPMAP_NONE,  Allocation.USAGE_SCRIPT & Allocation.USAGE_SHARED);
        _outBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        
        _filtersLib.set_gScript(_filtersLib);
        _filtersLib.set_gIn(_inData);
        _filtersLib.set_gOut(_outData);
        _filtersLib.set_width(w);
        _filtersLib.set_height(h);
    }
    
    // handling camera object (and related) through activity states
    @Override
    protected void onResume() {
            super.onResume();
            
            _camera = Camera.open();
            
            Parameters cameraParameters = _camera.getParameters();
            
            // get preview size and init buffers accordingly
            Size previewSize = cameraParameters.getPreviewSize();
            // could chose another size here
            initStuff(previewSize.width, previewSize.height); 
            
            // init preview callback
            _camera.setPreviewCallbackWithBuffer(_previewCallback);
            
            // commented out as this just rotates preview on surface, 
            // and not data received in preview callback
            //
            //rotate preview to match screen orientation
            //p.set("orientation", "portrait");
            //cameraParameters.setRotation(90);
            
            // try setting continuous auto focus
            List<String> focusModes = cameraParameters.getSupportedFocusModes();
            if (focusModes.contains( Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO); 
            }
            
            _camera.setParameters(cameraParameters);
            
            _camera.startPreview();
    }

    PreviewCallback _previewCallback = new PreviewCallback(){
        public void onPreviewFrame(byte[] data, Camera camera) {

            _inData.copy1DRangeFrom(0, _previewSamples, data);
            _filtersLib.forEach_GetBorder(_inData, _outData);
            _outData.copyTo(_outBmp);
            // TODO: rotate bmp
            _imageView.setImageBitmap(_outBmp);
            _imageView.invalidate();
                    
            camera.addCallbackBuffer(data);
        }
    };
    
    @Override
    protected void onPause() {
            if(_camera != null) {
                    _camera.stopPreview();
                    _camera.release();
                    _camera = null;
            }
            super.onPause();
    }

    public void surfaceCreated(SurfaceHolder holder) {
        try {
            if (_camera != null) {
                _camera.setPreviewDisplay(holder);
            }
        } catch (IOException exception) {
            Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
        }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        //
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        if (_camera != null) {
            _camera.stopPreview();
        }
    }

}
