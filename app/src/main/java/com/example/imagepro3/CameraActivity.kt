package com.example.imagepro3

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
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
import org.opencv.android.*
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
import org.opencv.core.*
import org.opencv.core.Core.*
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.ORB
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.HOGDescriptor
import org.opencv.video.BackgroundSubtractorMOG2
import org.opencv.video.KalmanFilter
import org.opencv.video.Video
import org.opencv.video.Video.createBackgroundSubtractorMOG2
import java.io.File
import java.lang.System.exit
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.system.exitProcess

class CameraActivity : Activity(), CvCameraViewListener2, SensorEventListener {
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
    private var pictureMat = Mat()

    // Detecting
    private var prevContour : MatOfPoint? = null
    private var prevArea = 0.0
    private var prevObjectMat : Mat? = null
    private var prevOpenCVX : Float? = null
    private var prevOpenCVY : Float? = null

    // 카메라 미리보기 이미지의 width, height
    private var mFrameWidth : Float? = null
    private var mFrameHeight : Float? = null

    // Tracking
    private var objectMatch : Boolean = true
    private var prevOpenCVYWidth: Int = 0
    private var prevOpenCVYHeight: Int = 0

    // 자이로스코프
    lateinit var sensorManager: SensorManager
    private var rotationSpeed_x : Float? = null
    private var rotationSpeed_y : Float? = null



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
        // 센서 설정
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
            SensorManager.SENSOR_DELAY_NORMAL
        )
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
        mRgba = Mat(height, width, CvType.CV_8UC3)
        //mGray = Mat(height, width, CvType.CV_8UC1)
        mFrameWidth = width.toFloat()
        mFrameHeight = height.toFloat()
    }

    override fun onCameraViewStopped() {
        mRgba!!.release()
    }

    // 여기서 카메라 프레임을 처리
    override fun onCameraFrame(inputFrame: CvCameraViewFrame): Mat {
        // 프레임 처리 로직
//        val startTime = Core.getTickCount()

        mRgba = inputFrame.rgba()
        //mGray = inputFrame.gray()
        var mat : Mat? = mRgba
        var contours = ArrayList<MatOfPoint>()
        pictureMat = mat!!

        if (touchOnOff) {
            // 윤곽선으로 탐지
            mat = cannyEdgeDetection(mRgba!!)
            //val backSubMat = backgroundSubtractor(mat!!)
            if (prevContour == null) {
                contours = firstGetContours(mat!!, mRgba!!)
            } else {
                contours = getContours(mat!!, mRgba!!)
            }


            // Histogram backprojection 기반 탐지
//            if (prevContour == null) {
//                mat = cannyEdgeDetection(mRgba)
//                contours = firstGetContours(mat!!, mRgba!!)
//            } else {
//                mat = camShiftDemo(mat!!)
//                return mat
//            }

            // kalmanFilter 예측 값으로 이동
            if (!objectMatch) {
                val pairPoint = objectToKalmanFilter()
                val contour = contours.first()
                contours.clear()
                contours.add(moveObjectPoint(pairPoint.first, pairPoint.second, contour))
                // Gyroscope 값으로 이동
                // (0, 0)으로 자꾸 이동함.
//                val contour2 = moveObjectShift(changeObjectToGyroscope("x"), changeObjectToGyroscope("y"), contour)
//                contours.clear()
//                contours.add(contour2)
            }

            if (contours.isNotEmpty()) {
                val imgContour = mRgba
                drawContour(contours, imgContour!!)
                return imgContour!!
            } else {
                return mRgba!!
            }
        } else {
            prevContour = null
            prevObjectMat = null
            objectMatch = true
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

        return mRgba!!
    }

    companion object {
        private const val TAG = "MainActivity"
    }

    // <--------------------------------------------------------------------------------------------------------------------------------->


    // <--------------------------------------------------------------------------------------------------------------------------------->
    // 함수들

    //objectDetectionFromFeature
    private fun cannyEdgeDetection(mat : Mat?) : Mat?{
        if (mat == null)
            return mat

        val img = mat
        //var imgContour = img.clone()

        // 1. Convert into Grayscale
        val imgGray = Mat()
        Imgproc.cvtColor(img, imgGray, Imgproc.COLOR_BGR2GRAY)

        // 2. Detect edges
        val imgBlur = Mat()
        Imgproc.GaussianBlur(imgGray, imgBlur, Size(7.0, 7.0), 1.0)
        val imgCanny = Mat()
        Imgproc.Canny(imgBlur, imgCanny, 50.0, 50.0)

        // 3. Get Contour
        //imgContour = getContours(imgCanny, imgContour)
//        val imgBlack = Mat.zeros(img.size(), CvType.CV_8UC3)

        return imgCanny
    }

    private fun firstGetContours(img: Mat, imgContour: Mat) : ArrayList<MatOfPoint> {
        var contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(img, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE) // CHAIN_APPROX_SIMPLE, CHAIN_APPROX_NONE

        // 터치 좌표 근처의 contours
        contours = searchContourNearTouch(contours)

        var bigContour = MatOfPoint()
        var bigArea = 0.0

        // HuMoments 객체 특징 비교
        // 처음 클릭시
        for (cnt in contours) {
            val area = Imgproc.contourArea(cnt)
            if (area > 500 && area > bigArea) {
                bigArea = area
                bigContour = cnt
            }
        }

        // 못 찾은 경우
        if (bigContour.size().height == 0.0) {
            contours.clear()
            return contours
        }
        else {
            // Object가 정해졌을 때
            prevContour = bigContour
            prevObjectMat = imgContour
            prevArea = Imgproc.contourArea(bigContour)

            val moments = Imgproc.moments(bigContour)
            prevOpenCVX = (moments.m10 / moments.m00).toFloat()
            prevOpenCVY = (moments.m01 / moments.m00).toFloat()
            prevOpenCVYWidth = bigContour.width()
            prevOpenCVYHeight = bigContour.height()
            // 칼만 필터 시작.
            kalmanInit((moments.m10 / moments.m00).toFloat(), (moments.m01 / moments.m00).toFloat())
        }

        // contour 하나만 그리기.
        contours.clear()
        contours.add(bigContour)

        return contours
    }

    private fun getContours(img: Mat, imgContour: Mat) : ArrayList<MatOfPoint> {
        var contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(img, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE) // CHAIN_APPROX_SIMPLE, CHAIN_APPROX_NONE

        // 터치 좌표 근처의 contours
        contours = searchContourNearTouch(contours)

        var bigContour = MatOfPoint()
        var bigArea = 0.0

        // HuMoments 객체 특징 비교
        // 처음 클릭 이후
        val grayImage = Mat()
        Imgproc.cvtColor(imgContour, grayImage, Imgproc.COLOR_BGR2GRAY)
//            val singleChannelImage = Mat()
//            Imgproc.cvtColor(grayImage, singleChannelImage, Imgproc.COLOR_GRAY2BGR)
        val prevPartMat = convertMatOfPointToMat(prevContour!!, prevObjectMat!!)
        var smallDistant = Double.MAX_VALUE

        for (cnt in contours) {
            val area = Imgproc.contourArea(cnt)
            //val distant = compareOBR(prevPartMat, convertMatOfPointToMat(cnt, grayImage))
            val distant = compareHuMoments(prevPartMat, convertMatOfPointToMat(cnt, grayImage))
            //val distant = Double.MAX_VALUE
            println("abs(prevArea - area) : " + abs(prevArea - area))
            if (abs(prevArea - area) < 2000 && distant < smallDistant && area > 500) {
                smallDistant = distant
                bigContour = cnt
            }
        }

        val moments = Imgproc.moments(bigContour)
        // object 유사한 것 못찾음
        if (smallDistant == Double.MAX_VALUE) {
            objectMatch = false
            // Object가 정해지지 않았을때
            bigContour = prevContour!!
            println("Not Match")
        }
        // object 유사한 것 찾음
        else {
            objectMatch = true
            // kalmanFilter 저장
            storeObjectToKalmanFilter((moments.m10 / moments.m00).toFloat(), (moments.m01 / moments.m00).toFloat())
            println("Match")

            // Object가 정해졌을 때
            prevContour = bigContour
            prevObjectMat = imgContour
            prevArea = Imgproc.contourArea(bigContour)

            val moments = Imgproc.moments(bigContour)
            prevOpenCVX = (moments.m10 / moments.m00).toFloat()
            prevOpenCVY = (moments.m01 / moments.m00).toFloat()
            prevOpenCVYWidth = bigContour.width()
            prevOpenCVYHeight = bigContour.height()
        }

        // contour 하나만 그리기.
        contours.clear()
        contours.add(bigContour)

        return contours
    }

    private fun drawContour(contours : ArrayList<MatOfPoint>, imgContour: Mat) : Mat {
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

    private fun searchContourNearTouch(contours : ArrayList<MatOfPoint>) : ArrayList<MatOfPoint> {
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

    private fun compareOBR(image1: Mat, image2: Mat): Double {
        val orb = ORB.create()

        val descriptors1 = Mat()
        val keypoints1 = MatOfKeyPoint()
        orb.detectAndCompute(image1, Mat(), keypoints1, descriptors1)

        val descriptors2 = Mat()
        val keypoints2 = MatOfKeyPoint()
        orb.detectAndCompute(image2, Mat(), keypoints2, descriptors2)

//        val matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)
//        val matches = MatOfDMatch()
//        matcher.match(descriptors1, descriptors2, matches)

        val matcher = DescriptorMatcher.create(DescriptorMatcher.FLANNBASED)
        val matches = MatOfDMatch()
        matcher.match(descriptors1, descriptors2, matches)

        // threshold 생성
        val matchList = matches.toList()
        val distances = matchList.map { it.distance }
        val sortedDistances = distances.sorted()
        val n = 10 // 상위 n개의 값을 선택
        val threshold = sortedDistances.take(n).average()

        return threshold
    }

    private fun takePicture() {
        val picture = pictureMat
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


    // SORT(Simple Online and Realtime Tracking)
    // KalmanFilter
    private val kalman = KalmanFilter(4, 2, 0)
    // Tracking
    private fun kalmanInit(x : Float, y : Float) {
        // Initialize Kalman filter
        kalman._transitionMatrix = Mat(4, 4, CvType.CV_32F, Scalar(0.0))
        kalman._transitionMatrix.put(0, 0, 1.0)
        kalman._transitionMatrix.put(1, 1, 1.0)
        kalman._transitionMatrix.put(2, 2, 1.0)
        kalman._transitionMatrix.put(3, 3, 1.0)
        kalman._measurementMatrix = Mat(2, 4, CvType.CV_32F, Scalar(0.0))
        kalman._measurementMatrix.put(0, 0, 1.0)
        kalman._measurementMatrix.put(1, 1, 1.0)
        kalman._processNoiseCov = Mat(4, 4, CvType.CV_32F, Scalar(0.0))
        kalman._processNoiseCov.put(0, 0, 1e-3)
        kalman._processNoiseCov.put(1, 1, 1e-3)
        kalman._processNoiseCov.put(2, 2, 1e-3)
        kalman._processNoiseCov.put(3, 3, 1e-3)
        kalman._measurementNoiseCov = Mat(2, 2, CvType.CV_32F, Scalar(0.0))
        kalman._measurementNoiseCov.put(0, 0, 1e-1)
        kalman._measurementNoiseCov.put(1, 1, 1e-1)
        kalman._errorCovPost = Mat(4, 4, CvType.CV_32F, Scalar(0.0))
        kalman._errorCovPost.put(0, 0, floatArrayOf(x))
        kalman._errorCovPost.put(1, 1, floatArrayOf(y))
        kalman._errorCovPost.put(2, 2, 1.0)
        kalman._errorCovPost.put(3, 3, 1.0)
    }

    private fun storeObjectToKalmanFilter(answerMatX : Float, answerMatY : Float) {
        val prediction = kalman.predict()
        val measurement = Mat(2, 1, CvType.CV_32F)
        measurement.put(0, 0, floatArrayOf(answerMatX))
        measurement.put(1, 0, floatArrayOf(answerMatY))
        kalman.correct(measurement)

//        prevMeasurement = measurement
    }

    private fun objectToKalmanFilter() : Pair<Float, Float>{
        // Predict and update Kalman filter
        val prediction = kalman.predict()
//        val measurement = Mat(2, 1, CvType.CV_32F)
//        kalman.correct(measurement)

//        val state = kalman._statePost
//        val predictedX = state.get(0, 0)[0]
//        val predictedY = state.get(1, 0)[0]

        val predictedX = prediction.get(0, 0)[0]
        val predictedY = prediction.get(1, 0)[0]
        val result = Pair<Float, Float>(predictedX.toFloat(), predictedY.toFloat())

        return result
    }

    // 자이로스코프
    override fun onSensorChanged(event: SensorEvent?) {

        val x = event?.values?.get(0) as Float // y축 기준으로 핸드폰 앞쪽(시계) - 뒤로(반시계) +
        val y = event?.values?.get(1) as Float // z축 기준으로 핸드폰 시계 - 반시계 +
        val z = event?.values?.get(2) as Float // x축 기준으로 핸드폰 시계 - 반시계 +

        rotationSpeed_x = x
        rotationSpeed_y = y

        //println(x.toString() + ", " + y.toString() + ", " + z.toString())
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }

    private fun changeObjectToGyroscope(str : String) : Float{
        val density = resources.displayMetrics.density
        var constantNumVctX = 10.0f * density
        var constantNumVctY = 10.0f * density

        //println(rotationSpeed_x!! * constantNumVctX)
        //println(rotationSpeed_y!! * constantNumVctY)

        when (str) {
            "x" -> {
                return rotationSpeed_x!! * constantNumVctX
            }
            "y" -> {
                return rotationSpeed_y!! * constantNumVctY
            }
        }
        return 0.0f
    }

    private fun moveObjectPoint(x : Float, y : Float, contour : MatOfPoint) : MatOfPoint{
        val moments = Imgproc.moments(contour)
        val centerX = (moments.m10 / moments.m00).toFloat()
        val centerY = (moments.m01 / moments.m00).toFloat()
        val shiftX = x - centerX // x 좌표 이동량
        val shiftY = y - centerY // y 좌표 이동량

        val shiftedPoints = contour.toArray().map { p -> Point(p.x + shiftX, p.y + shiftY) }.toTypedArray()
        val shiftedContour = MatOfPoint(*shiftedPoints)
        return shiftedContour
    }

//    private fun moveObjectShift(x : Float, y : Float, contour : MatOfPoint) : MatOfPoint{
//        val shiftX = x // x 좌표 이동량
//        val shiftY = y // y 좌표 이동량
//
//        val shiftedPoints = contour.toArray().map { p -> Point(p.x + shiftX, p.y + shiftY) }.toTypedArray()
//        val shiftedContour = MatOfPoint(*shiftedPoints)
//        return shiftedContour
//    }

    private fun moveObjectShift(x: Float, y: Float, contour: MatOfPoint): MatOfPoint {

        println("rotationSpeed_x = $rotationSpeed_x")
        println("rotationSpeed_y = $rotationSpeed_y")

        // contour 점들의 좌표값을 가져옴
        val points = contour.toArray()
        // 새로운 점들의 좌표값 계산 및 새로운 Point 배열에 저장
        val shiftedPoints = points.map { p -> Point(p.x + x, p.y + y) }.toTypedArray()
        // 새로운 MatOfPoint 객체 생성
        val shiftedContour = MatOfPoint(*shiftedPoints)
        return shiftedContour
    }

    // Multi-Object Tracking (MOT)
//    private val mog2: BackgroundSubtractorMOG2 get() = createBackgroundSubtractorMOG2()
//    //private val kalman = KalmanFilter(4, 2, 0)
//    private var state = Mat.zeros(4, 1, CvType.CV_32F)
//    private var measurement = Mat.zeros(2, 1, CvType.CV_32F)
//    fun processing(frame: Mat): Mat {
//        // Create foreground mask using BackgroundSubtractorMOG2
//        val fgMask = Mat()
//        mog2.apply(frame, fgMask)
//
//        // Find contours in foreground mask
//        val contours = mutableListOf<MatOfPoint>()
//        val hierarchy = Mat()
//        Imgproc.findContours(fgMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
//
//        // Select contour with largest area as target object
//        var maxContour: MatOfPoint? = null
//        var maxContourArea = 0.0
//        for (contour in contours) {
//            val contourArea = Imgproc.contourArea(contour)
//            if (contourArea > maxContourArea) {
//                maxContour = contour
//                maxContourArea = contourArea
//            }
//        }
//
//        // Initialize Kalman filter if target object is detected
//        if (maxContour != null) {
//            val rect = Imgproc.boundingRect(maxContour)
//            state.put(0, 0, rect.x + rect.width / 2.0)
//            state.put(1, 0, rect.y + rect.height / 2.0)
//            kalmanInit(state.get(0, 0)[0].toFloat(), state.get(1, 0)[0].toFloat())
//
//            //kalman.init(state, Mat.zeros(4, 4, CvType.CV_32F))
//            //kalman.init(Mat.eye(4, 4, CvType.CV_32F), Mat.eye(4, 4, CvType.CV_32F))
//
//            //kalmanInit(state.get(0, 0), )
//
//            // Update measurement matrix based on detected object location
//            measurement.put(0, 0, rect.x + rect.width / 2.0)
//            measurement.put(1, 0, rect.y + rect.height / 2.0)
//        }
//
//        // Predict object location using Kalman filter
//        val prediction = kalman.predict()
//
//        // Update state using measurement if object is detected
//        if (maxContour != null) {
//            kalman.correct(measurement)
//        }
//
//        // Draw object location on frame
//        val x = prediction.get(0, 0)[0].toFloat()
//        val y = prediction.get(1, 0)[0].toFloat()
//        Imgproc.circle(frame, Point(x.toDouble(), y.toDouble()), 5, Scalar(0.0, 255.0, 0.0), -1)
//
//        return frame
//    }

    private val mog2: BackgroundSubtractorMOG2 get() = createBackgroundSubtractorMOG2()
    private fun backgroundSubtractor(frame: Mat) : Mat {
        val grayFrame = Mat()
        Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY)

        val fgMask = Mat()
        mog2.apply(grayFrame, fgMask, -1.0)

        return fgMask
    }

    fun processing(frame: Mat): MatOfPoint? {
        // Create foreground mask using BackgroundSubtractorMOG2
        val fgMask = Mat()
        mog2.apply(frame, fgMask)

        // Find contours in foreground mask
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(fgMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        // Select contour with largest area as target object
        var maxContour: MatOfPoint? = null
        var maxContourArea = 0.0
        for (contour in contours) {
            val contourArea = Imgproc.contourArea(contour)
            if (contourArea > maxContourArea) {
                maxContour = contour
                maxContourArea = contourArea
            }
        }

        // Return the largest contour
        return maxContour
    }


    // CamShift
    private fun camShiftDemo(mat : Mat) : Mat{
        // Load an image
        var frame = mat

        // Convert the image to HSV color space
        val hsv = Mat()
        Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV)

        // Define the range of the target color in HSV space
//        val lowerBound = Scalar(0.0, 60.0, 32.0)
//        val upperBound = Scalar(180.0, 255.0, 255.0)
//        val lowerBound = Scalar(0.0, 100.0, 100.0)
//        val upperBound = Scalar(10.0, 255.0, 255.0)

        // 3. 추적된 객체를 이용하여 HSV 색공간에서의 대상 색상 범위 계산
//        val contourRect = Imgproc.boundingRect(prevContour)
//        val roiHsv = hsv.submat(contourRect)
//        val meanHsv = Core.mean(roiHsv)
//        val lowerBound = Scalar(meanHsv.`val`[0] - 10, meanHsv.`val`[1] - 30, meanHsv.`val`[2] - 30)
//        val upperBound = Scalar(meanHsv.`val`[0] + 10, meanHsv.`val`[1] + 30, meanHsv.`val`[2] + 30)
        val lowerBound = Scalar(0.0, 100.0, 100.0)
        val upperBound = Scalar(10.0, 255.0, 255.0)

        // Create a mask using the range of the target color
        val mask = Mat()
        Core.inRange(hsv, lowerBound, upperBound, mask)

        // Create a histogram of the masked image
        val hist = Mat()
        val hue = Mat(hsv.size(), hsv.type())
        val ch = MatOfInt(0, 0) // arrayOf(0, 0)
        Core.mixChannels(listOf(hsv), listOf(hue), ch)
        val histSize = MatOfInt(180)
        val histRange = MatOfFloat(0.0f, 180.0f)
        val histChannels = MatOfInt(0)
        Imgproc.calcHist(listOf(hue), histChannels, mask, hist, histSize, histRange)

        // Normalize the histogram
        Core.normalize(hist, hist, 0.0, 255.0, Core.NORM_MINMAX)

        // Set the criteria for CamShift
        val criteria = TermCriteria(TermCriteria.EPS or TermCriteria.MAX_ITER, 10, 1.0)

        // Initialize the tracking window
//        var trackWindow : Rect? = null
//        if (prevContour == null) {
//            return mat
//        } else {
//            trackWindow = Rect(prevOpenCVX!!.toInt(), prevOpenCVY!!.toInt(), prevOpenCVYWidth, prevOpenCVYHeight)
//            val contours = ArrayList<MatOfPoint>()
//            contours.add(prevContour!!)
//            frame = drawContour(contours, frame)
//        }

        var trackWindow : Rect? = null
        if (prevOpenCVX != null)
            trackWindow = Rect(prevOpenCVX!!.toInt(), prevOpenCVY!!.toInt(), prevOpenCVYWidth, prevOpenCVYHeight)
        else {
            trackWindow = Rect(touch_x.toInt(), touch_y.toInt(), 100, 100)
        }

        Core.inRange(hsv, lowerBound, upperBound, mask)

        // Create a new histogram of the masked image
        Imgproc.calcHist(listOf(hue), histChannels, mask, hist, histSize, histRange)

        // Normalize the new histogram
        Core.normalize(hist, hist, 0.0, 255.0, Core.NORM_MINMAX)

        // Apply back projection to get the probabilities of the target color in each pixel
        val backProj = Mat()
        Imgproc.calcBackProject(listOf(hue), histChannels, hist, backProj, histRange, 1.0)

        // Apply CamShift to track the target object
        Video.CamShift(backProj, trackWindow, criteria)

        // Draw the tracking window on the image
        val leftP1 = Point(trackWindow.x.toDouble(), trackWindow.y.toDouble())
        val rightP2 = Point((trackWindow.x + trackWindow.width).toDouble(), (trackWindow.y + trackWindow.height).toDouble())
        Imgproc.rectangle(frame, leftP1, rightP2, Scalar(0.0, 255.0, 0.0), 3)

        // Release resources
//        Imgcodecs.destroyAllWindows()

        // 업데이트
        prevObjectMat = mat
        prevArea = trackWindow.area()
        prevOpenCVX = trackWindow.x.toFloat()
        prevOpenCVY = trackWindow.y.toFloat()
        prevOpenCVYWidth = trackWindow.width
        prevOpenCVYHeight = trackWindow.height

        return frame
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