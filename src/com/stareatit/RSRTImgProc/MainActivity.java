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
import android.graphics.ImageFormat;
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

public class MainActivity extends Activity
{
    private static final String TAG = "RSRTImgProcMain";
    
    Camera _camera = null;
    
    RenderScript _renderScript = null;
    ScriptC_filter _filtersLib = null;
    
    Allocation _inData = null;
    Allocation _tmpData = null;
    Allocation _outData = null;
    
    Bitmap _outBmp = null;
    
    int _previewSamples = 0;
    
    SurfaceView _livePreviewSurface;
    SurfaceHolder _livePreviewSurfaceHolder;
    
    ImageView _processedSurface;
    SurfaceHolder _processedSurfaceHolder;
    
    //ScriptGroup _sobelGroup;
    
    final Object _busyLock = new Object();
    boolean busy = false;
    
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        setContentView(R.layout.main);
              
        _livePreviewSurface = (SurfaceView)findViewById(R.id.previewView); 
        _livePreviewSurfaceHolder = _livePreviewSurface.getHolder();
        _livePreviewSurfaceHolder.addCallback(new SurfaceHolder.Callback() {

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
        }); 

        //deprecated in api level 11. Since here we need at least 14, it is no use.
        //mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        
        _livePreviewSurface.setKeepScreenOn(true);
        
        _processedSurface =  (ImageView)findViewById(R.id.processedView);
        
        // renderscript stuff
        
        _renderScript = RenderScript.create(this); // <<The RenderScript context can be destroyed with destroy() or by allowing the RenderScript context object to be garbage collected.>>
        _filtersLib = new ScriptC_filter(_renderScript);
    
        //(new Thread(this)).start();
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
        //tbIn.setZ(1);
        tbIn.setMipmaps(false);
        tbIn.setFaces(false);
        
        Type.Builder tbTmp = new Type.Builder(_renderScript, Element.F32(_renderScript));
        tbTmp.setX(w);
        tbTmp.setY(h);
        //tbTmp.setZ(1);
        tbTmp.setMipmaps(false);
        tbTmp.setFaces(false);
        
        Type.Builder tbOut = new Type.Builder(_renderScript, Element.RGBA_8888(_renderScript));
        tbOut.setX(w); 
        tbOut.setY(h);
        //tbOut.setZ(1);
        tbOut.setMipmaps(false);
        tbOut.setFaces(false);
        
        _inData = Allocation.createTyped(_renderScript, tbIn.create(), Allocation.MipmapControl.MIPMAP_NONE,  Allocation.USAGE_SCRIPT & Allocation.USAGE_SHARED);
        _tmpData = Allocation.createTyped(_renderScript, tbTmp.create(), Allocation.MipmapControl.MIPMAP_NONE,  Allocation.USAGE_SCRIPT & Allocation.USAGE_SHARED);
        _outData = Allocation.createTyped(_renderScript, tbOut.create(), Allocation.MipmapControl.MIPMAP_NONE,  Allocation.USAGE_SCRIPT & Allocation.USAGE_SHARED);
        _outBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        
        _processedSurface.setImageBitmap(_outBmp);
                
        _filtersLib.set_gScript(_filtersLib);
        _filtersLib.set_gIn(_inData);
        _filtersLib.set_gTmp(_tmpData);
        _filtersLib.set_gOut(_outData);
        _filtersLib.set_width(w);
        _filtersLib.set_height(h);
    }
    
    private Size getNearestSize(int sw, int sh, List<Size> sizes)
    {
        int err = 1000000;
        int idx = -1;
        for (int i=0;i<sizes.size(); ++i)
        {
            Size ns = sizes.get(i);
            int newErr = Math.abs(ns.width - sw) + Math.abs(ns.height - sh);
            if ((idx<0)|| (newErr<err))
            {
                err = newErr;
                idx = i;
            }
        }
        return sizes.get(idx);
    }
    
    private int[] getFastestFpsRange(List<int[]> ranges)
    {
        int max = 0;
        int idx = -1;
        for (int i=0;i<ranges.size(); ++i)
        {
            int[] range = ranges.get(i);
            if ((idx<0)||(range[1]>max))
            {
                max = range[1];
                idx = i;
            }
        }
        return ranges.get(idx);
    }
    
    // handling camera object (and related) through activity states
    @Override
    protected void onResume() {
            super.onResume();
            
            _camera = Camera.open();
            
            Parameters cameraParameters = _camera.getParameters();
            
            cameraParameters.setPreviewFormat(ImageFormat.NV21); // surely available
            Size s = getNearestSize(640,480, cameraParameters.getSupportedPreviewSizes()); // 640x480 arbitrarily chosen
            cameraParameters.setPreviewSize(s.width, s.height);
            
            int[] range = getFastestFpsRange(cameraParameters.getSupportedPreviewFpsRange());
            cameraParameters.setPreviewFpsRange(range[0], range[1]);
            
            // get actual preview size and init buffers accordingly
            Size previewSize = cameraParameters.getPreviewSize();
            
            initStuff(previewSize.width, previewSize.height); 
            
            // try setting continuous auto focus
            List<String> focusModes = cameraParameters.getSupportedFocusModes();
            if (focusModes.contains( Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO); 
            }
            
            // init preview callback
            _camera.setPreviewCallbackWithBuffer(_previewCallback);
            
            // commented out as this just rotates preview on surface, 
            // and not data received in preview callback
            //
            //rotate preview to match screen orientation
            //p.set("orientation", "portrait");
            //cameraParameters.setRotation(90);
            
            
            _camera.setParameters(cameraParameters);
            
            _camera.startPreview();
    }

    PreviewCallback _previewCallback = new PreviewCallback(){
        public void onPreviewFrame(byte[] data, Camera camera) {

            synchronized(_busyLock)
            {
                if (busy) // safely skip frames if we're not ready to process next
                {
                    camera.addCallbackBuffer(data);
                    return; 
                }
                busy = true;
            }
            
            try
            {
                // copy data from preview buffer to allocation
                _inData.copy1DRangeFrom(0, _previewSamples, data);
                
                // CALL THE ACTUAL RS FILTER
                
                // single kernel call
                //_filtersLib.forEach_GetBorder(_inData, _outData);
                
                // two calls to execute a two-pass filter
                _filtersLib.forEach_SobelFirstPass(_inData, _tmpData);
                _filtersLib.forEach_SobelSecondPass(_inData, _outData);
                
                // get output
                _outData.copyTo(_outBmp);
                
                // TODO: rotate bmp
                
                // display outcome
                _processedSurface.invalidate();
            }
            catch (Exception e){}
            
            camera.addCallbackBuffer(data);
            
            busy = false;
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

}
