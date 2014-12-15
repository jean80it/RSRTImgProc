#pragma version(1) 
#pragma rs java_package_name(com.stareatit.RSRTImgProc)
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



// use of globals to operate on whole image (GetBorder and SimpleBlur)
rs_allocation gIn;
//rs_allocation gIn2;
rs_allocation gTmp;
rs_allocation gOut;
rs_script gScript;
int width;
int height;

void init () 
{ 
    // optional function
    // executed once when setting up the script;
} 

// separable function: performances could improve greatly implementing 
// this as a horizontal+vertical pass filter (next time)!
void SimpleBlur(const uchar *v_in, uchar4 *v_out, const void *usrData, uint32_t x, uint32_t y) 
{
    const float k1 = 0.1715728f; // w = 2
    const float k2 = 0.0857864f; // w = 1
    const float k3 = 0.0606601f; // w = 1/1.4 = 0.7

    uint32_t n = max(y - 1, (uint32_t)0);
    uint32_t s = min(y + 1, (uint32_t)height);
    uint32_t e = min(x + 1, (uint32_t)width);
    uint32_t w = max(x - 1, (uint32_t)0);
    
    // elementXY is the element of a 3x3 matrix centered around current sample
    const uchar *e11 = rsGetElementAt(gIn, w, n);
    const uchar *e21 = rsGetElementAt(gIn, x, n);
    const uchar *e31 = rsGetElementAt(gIn, e, n);

    const uchar *e12 = rsGetElementAt(gIn, w, y);
    const uchar *e22 = rsGetElementAt(gIn, x, y); 
    const uchar *e32 = rsGetElementAt(gIn, e, y);

    const uchar *e13 = rsGetElementAt(gIn, w, s);
    const uchar *e23 = rsGetElementAt(gIn, x, s);
    const uchar *e33 = rsGetElementAt(gIn, e, s);

    uchar res = (uchar)( *e22 * k1 +
                (*e21 + *e12  + *e32  + *e23) * k2 + 
                (*e11 + *e31  + *e13 + *e33) * k3);

    *v_out = (uchar4){res, res, res, 255};
}

// 1 0 -1
// 2 0 -2
// 1 0 -1
//
void SobelFirstPass(const uchar *v_in, float *v_out, const void *usrData, uint32_t x, uint32_t y) 
{
    uint32_t n = max(y - 1, (uint32_t)0);
    uint32_t s = min(y + 1, (uint32_t)height);
    uint32_t e = min(x + 1, (uint32_t)width);
    uint32_t w = max(x - 1, (uint32_t)0);

    const uchar *e11 = rsGetElementAt(gIn, w, n); 
    const uchar *e12 = rsGetElementAt(gIn, w, y); 
    const uchar *e13 = rsGetElementAt(gIn, w, s);
    
    const uchar *e31 = rsGetElementAt(gIn, e, n); 
    const uchar *e32 = rsGetElementAt(gIn, e, y); 
    const uchar *e33 = rsGetElementAt(gIn, e, s);

    *v_out =(*e12 - *e32)*2 + *e11 + *e13 - *e31 - *e33;
}

//  1  2  1
//  0  0  0
// -1 -2 -1
//
void SobelSecondPass(const uchar *v_in, uchar4 *v_out, const void *usrData, uint32_t x, uint32_t y) 
{
    uint32_t n = max(y - 1, (uint32_t)0);
    uint32_t s = min(y + 1, (uint32_t)height);
    uint32_t e = min(x + 1, (uint32_t)width);
    uint32_t w = max(x - 1, (uint32_t)0);

    const uchar *e11 = rsGetElementAt(gIn, w, n); 
    const uchar *e21 = rsGetElementAt(gIn, x, n); 
    const uchar *e31 = rsGetElementAt(gIn, e, n);
    
    const uchar *e13 = rsGetElementAt(gIn, w, s); 
    const uchar *e23 = rsGetElementAt(gIn, x, s); 
    const uchar *e33 = rsGetElementAt(gIn, e, s);

    const float *lastPassResult = rsGetElementAt(gTmp, x, y);

    float tmp = (*e21 - *e23)*2 + *e11 - *e13 + *e31 - *e33;

    uchar res =  (uchar)clamp(sqrt(tmp * tmp + *lastPassResult * *lastPassResult), 0.0f, 255.0f);
    
    *v_out = (uchar4){res, res, res, 255};
}

//void SepBlurH(const uchar *v_in, uint16_t *v_out, const void *usrData, uint32_t x, uint32_t y) 
//{
//    uint32_t e = min(x + 1, (uint32_t)width);
//    uint32_t w = max(x - 1, (uint32_t)0);
//
//    const uchar *e1 = rsGetElementAt(gIn, w, y);
//    const uchar *e2 = rsGetElementAt(gIn, x, y); 
//    const uchar *e3 = rsGetElementAt(gIn, e, y);
//
//    *v_out = (uint16_t)*e2 * (uint16_t)2 + (uint16_t)*e1  + (uint16_t)*e3;
//}

//void SepBlurV(const uint16_t *v_in, uint16_t *v_out, const void *usrData, uint32_t x, uint32_t y) 
//{
//    uint32_t n = max(y - 1, (uint32_t)0);
//    uint32_t s = min(y + 1, (uint32_t)height);
//
//    const uint16_t *e1 = rsGetElementAt(gIn, x, n);
//    const uint16_t *e2 = rsGetElementAt(gIn, x, y); 
//    const uint16_t *e3 = rsGetElementAt(gIn, x, s);
//
//    *v_out = *e2 * (uint16_t)2 + *e1  + *e3;
//}

//// 1  0 -1 
//// +
//// 1  2  1
//void SobelH(const uchar *v_in, uint32_t *v_out1, uint32_t x, uint32_t y) 
//{
//    uint32_t e = min(x + 1, (uint32_t)width);
//    uint32_t w = max(x - 1, (uint32_t)0);
//
//    const uchar *e1 = rsGetElementAt(gIn, w, y); 
//    const uchar *e2 = rsGetElementAt(gIn, x, y); 
//    const uchar *e3 = rsGetElementAt(gIn, e, y);
//    
//    *v_out1 = *e3 - *e1;
//    //*v_out2 = *e2 * 2 + *e1 + *e3;
//}
//
////  1     1
////  0  +  2
//// -1     1
////
//void SobelV(const uchar *v_in, float *v_out, const void *usrData, uint32_t x, uint32_t y) 
//{
//    uint32_t n = max(y - 1, (uint32_t)0);
//    uint32_t s = min(y + 1, (uint32_t)height);
//    
//    const uchar *e1 = rsGetElementAt(gIn, x, n); 
//    const uchar *e2 = rsGetElementAt(gIn, x, y); 
//    const uchar *e3 = rsGetElementAt(gIn, x, s);
//
//    const uchar *e1b = rsGetElementAt(gIn2, x, n); 
//    const uchar *e3b = rsGetElementAt(gIn2, x, s);
//
//    float c1 = *e2 * 2 + *e1 + *e3;
//    float c2 = *e3b - *e1b;
//
//    *v_out = sqrt(c1*c1+c2*c2);
//}