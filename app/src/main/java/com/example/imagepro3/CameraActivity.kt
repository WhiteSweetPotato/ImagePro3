package com.example.imagepro3

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
import org.opencv.android.JavaCamera2View
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.core.Core.*
import org.opencv.imgproc.Imgproc

class CameraActivity : Activity(), CvCameraViewListener2 {
    private var mRgba: Mat? = null
    private var mGray: Mat? = null
    private var mOpenCvCameraView: CameraBridgeViewBase? = null
    private val mLoaderCallback: BaseLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                SUCCESS -> {
                    run {
                        Log.i(TAG, "OpenCv Is loaded")
                        mOpenCvCameraView!!.enableView()
                    }
                    run { super.onManagerConnected(status) }
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    init {
        Log.i(TAG, "Instantiated new " + this.javaClass)
    }

    // <--------------------------------------------------------------------------------------------------------------------------------->

//    // 변수 선언
//    // SIFT
//    val sift = SIFT.create()
//    // SURF
//    val surf = SURF.create()
//    // HOG
//    val hog = HOGDescriptor()
//    // LBP
//    val lbp = LBPHFaceRecognizer.create()

    // 프레임 측정
    private val tickFrequency = getTickFrequency()
    private val time = getTickCount()



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val MY_PERMISSIONS_REQUEST_CAMERA = 0
        // if camera permission is not given it will ask for it on device
        if (ContextCompat.checkSelfPermission(this@CameraActivity, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(
                this@CameraActivity,
                arrayOf(Manifest.permission.CAMERA),
                MY_PERMISSIONS_REQUEST_CAMERA
            )
        }
        setContentView(R.layout.activity_camera)
        mOpenCvCameraView = findViewById<JavaCamera2View>(R.id.frame_Surface) as CameraBridgeViewBase
        mOpenCvCameraView!!.visibility = SurfaceView.VISIBLE
        mOpenCvCameraView!!.setCameraIndex(0) // adjust camera index if necessary
        //mOpenCvCameraView!!.setDisplayOrientation(90) // set display orientation to portrait
        //mOpenCvCameraView!!.setMaxFrameSize(640, 480)
        //mOpenCvCameraView!!.setFrameRate(15)
        mOpenCvCameraView!!.setCvCameraViewListener(this)
    }

    override fun onResume() {
        super.onResume()
        if (OpenCVLoader.initDebug()) {
            //if load success
            Log.d(TAG, "Opencv initialization is done")
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        } else {
            //if not loaded
            Log.d(TAG, "Opencv is not loaded. try again")
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback)
        }
    }

    override fun onPause() {
        super.onPause()
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView!!.disableView()
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView!!.disableView()
        }
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        mRgba = Mat(height, width, CvType.CV_8UC4)
        mGray = Mat(height, width, CvType.CV_8UC1)
    }

    override fun onCameraViewStopped() {
        mRgba!!.release()
    }

    // 여기서 카메라 프레임을 처리
    override fun onCameraFrame(inputFrame: CvCameraViewFrame): Mat {
        mRgba = inputFrame.rgba()
        mGray = inputFrame.gray()

        val mat = objectDetectionFromFeature(mRgba)

        val elapsed = (getTickCount() - time) / tickFrequency
        val fps = 1 / elapsed
        println("fps : $fps")

        return mat!!
    }

    companion object {
        private const val TAG = "MainActivity"
    }

    // <--------------------------------------------------------------------------------------------------------------------------------->
    // 터치 이벤트
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                println(event.x)
                println(event.y)
                println("터치댐")
            }
            MotionEvent.ACTION_UP -> {

            }
            MotionEvent.ACTION_MOVE -> {

            }
        }
        return super.onTouchEvent(event)
    }

    // <--------------------------------------------------------------------------------------------------------------------------------->
    // 함수들

    fun objectDetectionFromFeature(mat : Mat?) : Mat?{
        if (mat == null)
            return mat

        val img = mat
        var imgContour = img.clone()

        // 1. Convert into Grayscale
        val imgGray = Mat()
        Imgproc.cvtColor(img, imgGray, Imgproc.COLOR_BGR2GRAY)

        // 2. Detect edges
        val imgBlur = Mat()
        Imgproc.GaussianBlur(imgGray, imgBlur, Size(7.0, 7.0), 1.0)
        val imgCanny = Mat()
        Imgproc.Canny(imgBlur, imgCanny, 50.0, 50.0)

        // 3. Get Contour
        imgContour = getContours(imgCanny, imgContour)
//        val imgBlack = Mat.zeros(img.size(), CvType.CV_8UC3)
        return imgContour
    }


    fun getContours(img: Mat, imgContour: Mat) : Mat{
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(img, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE) // CHAIN_APPROX_SIMPLE

        for (cnt in contours) {
            val area = Imgproc.contourArea(cnt)
            //println(area)

            // 4. Draw Edges
            if (area > 500) {
                Imgproc.drawContours(imgContour, listOf(cnt), -1, Scalar(255.0, 0.0, 0.0), 3)

                val cnt2 = MatOfPoint2f(*cnt.toArray())
                // 5. Calculate the arc length
                val peri = Imgproc.arcLength(cnt2, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(MatOfPoint2f(*cnt.toArray()), approx, 0.02 * peri, true)
                //println(approx.toList())

                // 6. Object coners
                val objCor = approx.size().height.toInt()
                val rect = Imgproc.boundingRect(approx)

                // 7. Categorize the shapes
                val objType: String = when (objCor) {
                    3 -> "Tri"
                    4 -> {
                        val aspRatio = rect.width.toDouble() / rect.height.toDouble()
                        if (aspRatio > 0.95 && aspRatio < 1.05) "Sqr" else "Rec"
                    }
                    else -> if (objCor > 4) "Circle" else "None"
                }

                val leftP1 = Point(rect.x.toDouble(), rect.y.toDouble())
                val rightP2 = Point((rect.x + rect.width).toDouble(), (rect.y + rect.height).toDouble())

                Imgproc.rectangle(imgContour, leftP1, rightP2, Scalar(0.0, 255.0, 0.0), 2)

//                Imgproc.putText(
//                    imgContour, objType, Point(rect.x + rect.width / 2.0, rect.y + rect.height / 2.0),
//                    FONT_HERSHEY_SIMPLEX, 0.7, Scalar(0.0, 0.0, 0.0), 2
//                )
            }
        }

        return imgContour
    }



}