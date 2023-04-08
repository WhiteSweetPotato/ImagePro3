package com.example.imagepro3

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build.VERSION_CODES.S
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.Toast
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
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.HOGDescriptor
import java.io.File
import java.lang.System.exit
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.system.exitProcess

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

    // Touch on off
    private var touchOnOff : Boolean = false
    private var touchCount : Int = 0
    private var delay: Long = 230 // handler delay, 230 -> 0.23
    private val handler = Handler(Looper.getMainLooper())
    private var touch_x : Float = 0f
    private var touch_y : Float = 0f

    // Capture
    private var prevMat = Mat()

    // Detecting
    private var prevContour : MatOfPoint? = null
    private var prevArea = 0.0
    private var prevObjectMat : Mat? = null
    private var screenInside = true
    private var prevOpenCVX : Float? = null
    private var prevOpenCVY : Float? = null

    // 카메라 미리보기 이미지의 width, height
    private var mFrameWidth : Float? = null
    private var mFrameHeight : Float? = null

    // Tracking



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        // 상단바 숨기기
//        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
//        // 하단바 숨기기
//        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val MY_PERMISSIONS_REQUEST_CAMERA = 0
        // if camera permission is not given it will ask for it on device
        if (ContextCompat.checkSelfPermission(this@CameraActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(this@CameraActivity, arrayOf(Manifest.permission.CAMERA), MY_PERMISSIONS_REQUEST_CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this@CameraActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(this@CameraActivity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), MY_PERMISSIONS_REQUEST_CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this@CameraActivity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(this@CameraActivity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), MY_PERMISSIONS_REQUEST_CAMERA)
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

        mOpenCvCameraView!!.setOnTouchListener { view, event : MotionEvent->
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.performClick()
                    prevOpenCVX = null
                    prevOpenCVY = null
                    touch_x = event.x
                    touch_y = event.y
                    touchCount++
                    touchOnOff = !touchOnOff
                }
                MotionEvent.ACTION_UP -> {
                    view.performClick()
                }
                MotionEvent.ACTION_MOVE -> {
                    view.performClick()
                    handler.postDelayed({
                        if (touchCount > 0)
                            touchCount--
                    }, delay)
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    view.performClick()
                    // 두 개의 손가락으로 터치한 경우
                    touchCount = 2
                }
            }

            // 캡처 기능
            if (touchCount == 2) {
                takePicture()
                touchCount = 0
            }
            true
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
        //mGray = Mat(height, width, CvType.CV_8UC1)
        mFrameWidth = width.toFloat()
        mFrameHeight = height.toFloat()
    }

    override fun onCameraViewStopped() {
        mRgba!!.release()
    }

    // 여기서 카메라 프레임을 처리
    override fun onCameraFrame(inputFrame: CvCameraViewFrame): Mat {
        // Frame 측정
//        val startTime = Core.getTickCount()
        // 프레임 처리 로직

        mRgba = inputFrame.rgba()
        //mGray = inputFrame.gray()
        var mat : Mat? = mRgba
        prevMat = mat!!

//        print("touchOnOff : $touchOnOff")
//        print("prevContour : $prevContour")

        if (touchOnOff) {
            mat = objectDetectionFromFeature(mRgba)
        } else {
            prevContour = null
            prevObjectMat = null
            screenInside = true
        }

        // 끝 시간
//        val endTime = Core.getTickCount()
//        // 걸린 시간 계산
//        val elapsedTime = (endTime - startTime) / Core.getTickFrequency()
//        // FPS 계산
//        val fps = 1 / elapsedTime
//        // FPS 출력
//        println("fps : $fps")
        //println("touchCount : $touchCount")

        return mat!!
    }

    companion object {
        private const val TAG = "MainActivity"
    }

    // <--------------------------------------------------------------------------------------------------------------------------------->


    // <--------------------------------------------------------------------------------------------------------------------------------->
    // 함수들

    private fun objectDetectionFromFeature(mat : Mat?) : Mat?{
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

    private fun getContours(img: Mat, imgContour: Mat) : Mat{
        var contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(img, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE) // CHAIN_APPROX_SIMPLE, CHAIN_APPROX_NONE

        // 터치 좌표 근처의 contours
        contours = searchCountourNearTouch(contours)

        // 1. 기본
        var bigContour = MatOfPoint()
        var bigArea = 0.0
        // 처음 클릭시
        if (prevContour == null) {
            for (cnt in contours) {
                val area = Imgproc.contourArea(cnt)
                if (area > 500 && area > bigArea) {
                    bigArea = area
                    bigContour = cnt
                }
            }
            if (bigContour.size().height == 0.0) {
                return imgContour
            }
        }

        // 1. 윤곽선 하나만 그리기 면적으로 조절.
//        if (prevContour != null) {
//            for (cnt in contours) {
//                val area = Imgproc.contourArea(cnt)
//                if (area > 500 && area > bigArea) {
//                    bigArea = area
//                    bigContour = cnt
//                }
//            }
//        }
//        else {
//            for (cnt in contours) {
//                val area = Imgproc.contourArea(cnt)
//                if (abs(area - prevArea) < 10) {
//                    bigArea = area
//                    bigContour = cnt
//                }
//            }
//        }


        // ?. HOG 특징 추출 비교
        // bigContour = contourCompareWithHOG(prevContour, contours)
        //


        // 2. 윤곽선 유사도 검사
//        val copyContours = ArrayList<MatOfPoint>()
//        copyContours.addAll(contours)
//        contours.clear()
//        for (cnt in copyContours) {
//            val area = Imgproc.contourArea(cnt)
//            if (area > 500) {
//                contours.add(cnt)
//            }
//        }
//
//        if (prevContour != null) {
//            var SimilarContour : MatOfPoint? = findMostSimilarContour(contours, prevContour!!)
//            if (SimilarContour != null) {
//                bigContour = SimilarContour
//            }
//        }
//        else {
//            for (cnt in contours) {
//                val area = Imgproc.contourArea(cnt)
//                if (area > 500 && area > bigArea) {
//                    bigArea = area
//                    bigContour = cnt
//                }
//            }
//        }

        // 3. HuMoments 객체 특징 비교
        // 이후 움직일 때
        else {
            val grayImage = Mat()
            Imgproc.cvtColor(imgContour, grayImage, Imgproc.COLOR_BGR2GRAY)
//            val singleChannelImage = Mat()
//            Imgproc.cvtColor(grayImage, singleChannelImage, Imgproc.COLOR_GRAY2BGR)
            val prevPartMat = convertMatOfPointToMat(prevContour!!, prevObjectMat!!)
            var smallDistant = Double.MAX_VALUE

            for (cnt in contours) {
                val area = Imgproc.contourArea(cnt)
                val distant = compareHuMoments(prevPartMat, convertMatOfPointToMat(cnt, grayImage))
                //val distant = Double.MAX_VALUE
                if (abs(prevArea - area) < 10 && distant < smallDistant) {
                    smallDistant = distant
                    bigContour = cnt
                }
            }
            if (smallDistant == Double.MAX_VALUE) {
                //screenInside = false
                //return imgContour
                bigContour = prevContour!!
            }
            else {
                screenInside = true
            }
        }

        // 정해지지 않았을 때
        if (bigContour.size().height == 0.0) {
            bigContour = prevContour!!
        }
        else {
            // Object가 정해졌을 때
            prevContour = bigContour
            prevObjectMat = imgContour
            screenInside = true
            prevArea = Imgproc.contourArea(bigContour)

            val moments = Imgproc.moments(bigContour)

            prevOpenCVX = (moments.m10 / moments.m00).toFloat()
            prevOpenCVY = (moments.m01 / moments.m00).toFloat()
        }
        
        // 처음 object가 스크린 내에 있는가 없는가.
        if (!screenInside) {
            return imgContour
        }

        // contour 하나만 그리기.
        contours.clear()
        contours.add(bigContour)

        // contours Draw
        for (cnt in contours) {
            Imgproc.drawContours(imgContour, listOf(cnt), -1, Scalar(255.0, 0.0, 0.0), 3)

            val cnt2 = MatOfPoint2f(*cnt.toArray())
            // 5. Calculate the arc length
            val peri = Imgproc.arcLength(cnt2, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(MatOfPoint2f(*cnt.toArray()), approx, 0.02 * peri, true)

            // Object coners
            val objCor = approx.size().height.toInt()
            val rect = Imgproc.boundingRect(approx)

            // Categorize the shapes
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
        }

        return imgContour
    }

    private fun searchCountourNearTouch(contours : ArrayList<MatOfPoint>) : ArrayList<MatOfPoint> {
        val contoursNearTouch = ArrayList<MatOfPoint>()
        val dpValue = 50f // 변환하려는 dp 값
        val scale = resources.displayMetrics.density // 디바이스의 density 값을 가져옴
        var pixelValue = (dpValue * scale + 0.5f)// dp 값을 pixel 값으로 변환
//        val metrics = resources.displayMetrics
//        val widthDp = metrics.widthPixels / metrics.density
//        val heightDp = metrics.heightPixels / metrics.density

        // 화면과 사진의 x, y 값 조정.
        // 터치시 좌표는 세로모드, OpenCV Image는 가로모드.
        var openCVX = 0f
        var openCVY = 0f
        if (prevOpenCVX == null && prevOpenCVY == null) {
            openCVX = (touch_y) * mFrameWidth!! / mOpenCvCameraView!!.height
            openCVY = (mOpenCvCameraView!!.width - touch_x) * mFrameHeight!! / mOpenCvCameraView!!.width
            prevOpenCVX = openCVX
            prevOpenCVY = openCVY
        }
        else {
            pixelValue /= 2
            openCVX = prevOpenCVX!!
            openCVY = prevOpenCVY!!
        }

//        println("touch_x : $touch_x")
//        println("touch_y : $touch_y")
//        print("openCVX : $openCVX")
//        print("openCVY : $openCVY")
//        println("mFrameWidth : $mFrameWidth") // 960
//        println("mFrameHeight : $mFrameHeight") // 720
//        print("mOpenCvCameraView!!.width : " + mOpenCvCameraView!!.width) // 1080
//        print("mOpenCvCameraView!!.height :" + mOpenCvCameraView!!.height) // 2015

        for (contour in contours) {
            val moments = Imgproc.moments(contour)
            val centerX = (moments.m10 / moments.m00).toFloat()
            val centerY = (moments.m01 / moments.m00).toFloat()

            val dx = centerX - openCVX
            val dy = centerY - openCVY
            val distanceFromPoint = kotlin.math.sqrt((dx * dx + dy * dy))

            if (distanceFromPoint <= pixelValue) {
                contoursNearTouch.add(contour)
            }
        }

        return contoursNearTouch
    }

    private fun findMostSimilarContour(contours: ArrayList<MatOfPoint>, target: MatOfPoint): MatOfPoint? {
        var mostSimilar: MatOfPoint? = null
        var smallestDistance = Double.MAX_VALUE

        for (contour in contours) {
            val distance = Imgproc.matchShapes(target, contour, Imgproc.CV_CONTOURS_MATCH_I2, 0.0)
            if (distance < smallestDistance) {
                mostSimilar = contour
                smallestDistance = distance
            }
        }

        return mostSimilar
    }

    private fun convertMatOfPointToMat(contour: MatOfPoint, srcImage: Mat): Mat {
        val boundingRect = Imgproc.boundingRect(contour)
        val x = if (boundingRect.x >= 0) boundingRect.x else 0
        val y = if (boundingRect.y >= 0) boundingRect.y else 0
        val width = if (boundingRect.width + boundingRect.x <= srcImage.cols()) boundingRect.width else srcImage.cols() - boundingRect.x
        val height = if (boundingRect.height + boundingRect.y <= srcImage.rows()) boundingRect.height else srcImage.rows() - boundingRect.y
        val roi = srcImage.submat(y, y + height, x, x + width)
        val mat = Mat()
        roi.copyTo(mat)

        // 그레이스케일 이미지로 변환
        if (mat.channels() > 1) {
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY)
        }

        return mat
    }



    private fun compareHuMoments(image1: Mat, image2: Mat): Double {
        // Hu Moments 계산
        val moments1 = MatOfDouble()
        val moments2 = MatOfDouble()
        Imgproc.HuMoments(Imgproc.moments(image1), moments1)
        Imgproc.HuMoments(Imgproc.moments(image2), moments2)

        // Hu Moments 비교
        val distance = Core.norm(moments1, moments2, Core.NORM_L2)

        return distance
    }

    private fun takePicture() {
        val picture = prevMat
        val rotatePicture = Mat()
        Imgproc.cvtColor(picture, picture, Imgproc.COLOR_RGBA2BGR)
        Core.rotate(picture, rotatePicture, Core.ROTATE_90_CLOCKWISE)
        val fileName = "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)}/Camera/${System.currentTimeMillis()}.jpg"
        Imgcodecs.imwrite(fileName, rotatePicture)
        saveImageToGallery(fileName)
        Toast.makeText(this, "Saved to $fileName", Toast.LENGTH_SHORT).show()
    }

    private fun saveImageToGallery(imagePath: String) {
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        val f = File(imagePath)
        val contentUri = Uri.fromFile(f)
        mediaScanIntent.data = contentUri
        sendBroadcast(mediaScanIntent)
    }


    // 볼륨 키를 누르면 실행 되는 함수.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                //Toast.makeText(this, "Volume Up Pressed", Toast.LENGTH_SHORT).show()
                exit()
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                //Toast.makeText(this, "Volume Down Pressed", Toast.LENGTH_SHORT).show()
                exit()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // 종료
    private fun exit() {
        ActivityCompat.finishAffinity(this)
        exitProcess(0)
    }

    // <--------------------------------------------------------------------------------------------------------------------------------->
    // 종료

//    private fun compareContours(contour1: MatOfPoint, contour2: MatOfPoint): Double {
//        val numPoints = 300 // 원하는 point 개수로 설정
//        val shapeContext = ShapeContextDistanceExtractor.createSimpleShapeContext(numPoints)
//
//        // contour를 Point 배열로 변환
//        val points1 = contour1.toArray()
//        val points2 = contour2.toArray()
//
//        // shape context 계산
//        val descriptor1 = Mat()
//        val descriptor2 = Mat()
//        shapeContext.computeDescriptors(points1, descriptor1)
//        shapeContext.computeDescriptors(points2, descriptor2)
//
//        // 두 개의 descriptor 간의 거리 계산
//        return shapeContext.computeDistance(descriptor1, descriptor2)
//    }


    //    private fun compareContours(contour1: MatOfPoint, contour2: MatOfPoint): Double {
//        val numPoints = 300 // 원하는 point 개수로 설정
//        val shapeContext = ShapeContextDistanceExtractor.create(numPoints)
//        val shapeContext2 = shapeContextDis
//
//        // contour를 Point 배열로 변환
//        val points1 = contour1.toArray()
//        val points2 = contour2.toArray()
//
//        // shape context 계산
//        val descriptor1 = Mat()
//        val descriptor2 = Mat()
//        shapeContext.computeShapeContextDescriptors(points1, descriptor1)
//        shapeContext.computeShapeContextDescriptors(points2, descriptor2)
//
//        // 두 개의 descriptor 간의 거리 계산
//        return shapeContext.computeDistance(descriptor1, descriptor2)
//    }

//    // 파일 이름 중복 제거
//    private fun newJpgFileName(): String {
//        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss")
//        val filename = sdf.format(System.currentTimeMillis())
//        return "${filename}.jpg"
//    }

//    private fun take_picture_function(mRgba : Mat){
//        val saveMat = Mat()
//        // Core.flip(mRgba.t(), save_mat, 1)
//
//        Imgproc.cvtColor(mRgba, saveMat, Imgproc.COLOR_RGBA2BGR)
//
//        val folder : File = File(Environment.getExternalStorageDirectory().path + "/ImagePro")
//        var success : Boolean = true
//        if (!folder.exists()) {
//            success = folder.mkdir()
//        }
//        val sdf : SimpleDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss")
//        val currentDateAndTime = sdf.format(Date())
//        val fileName = Environment.getExternalStorageDirectory().path + "/ImagePro/" + currentDateAndTime + ".jpg"
//
//        //val galleryPath = Environment.getExternalStorageDirectory().toString() + "/DCIM/Camera/"
//
//        Imgcodecs.imwrite(fileName, saveMat)
//        saveImageToGallery(fileName)
//    }


}