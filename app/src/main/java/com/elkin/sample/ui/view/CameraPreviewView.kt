package com.elkin.sample.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.media.effect.Effect
import android.media.effect.EffectContext
import android.media.effect.EffectFactory
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.roundToInt

/**
 * @author elkin
 */
class CameraPreviewView(context: Context, attrs: AttributeSet? = null) : GLSurfaceView(
    context,
    attrs
), GLSurfaceView.Renderer, OnFrameAvailableListener {

    var surfaceTexture: SurfaceTexture? = null
    var surface: Surface? = null

    private val vertices = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    private val textureVertices = floatArrayOf(0f, 1f, 0f, 0f, 1f, 1f, 1f, 0f)

    private val verticesBuffer: FloatBuffer
    private val textureBuffer: FloatBuffer

    var vertexShader: Int = -1
    var fragmentShader: Int = -1
    var program: Int = -1

    var size = Size(1080, 1920)
    var textures = intArrayOf(0)

    private var GLInited = false
    var updateSurfaceTexture = false

    private var aspectRatio = 0f

    var onSurfaceReady: (() -> Unit)? = null

    init {
        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
        verticesBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }
        textureBuffer = ByteBuffer.allocateDirect(textureVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(textureVertices)
                position(0)
            }
    }

    override fun onPause() {
        super.onPause()
        surface?.release()
        surfaceTexture?.release()
        GLInited = false
        updateSurfaceTexture = false
    }

    fun setAspectRatio(width: Int, height: Int) {
        require(width > 0 && height > 0) { "Size cannot be negative" }
        aspectRatio = width.toFloat() / height.toFloat()
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        if (aspectRatio == 0f) {
            setMeasuredDimension(width, height)

        } else {
            val newWidth: Int
            val newHeight: Int
            val actualRatio = if (width > height) aspectRatio else 1f / aspectRatio
            if (width < height * actualRatio) {
                newHeight = height
                newWidth = (height * actualRatio).roundToInt()
            } else {
                newWidth = width
                newHeight = (width / actualRatio).roundToInt()
            }

            Log.d(LOG_TAG, "Measured dimensions set: $newWidth x $newHeight")
            setMeasuredDimension(newWidth, newHeight)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!GLInited) {
            return
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        synchronized(this) {
            if (updateSurfaceTexture) {
                surfaceTexture?.updateTexImage()
                updateSurfaceTexture = false
            }
        }

        GLES20.glUseProgram(program)

        val positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        val texturePositionHandle = GLES20.glGetAttribLocation(program, "vTexCoord")
        val textureHandle = GLES20.glGetUniformLocation(program, "sTexture")

        GLES20.glVertexAttribPointer(
            positionHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            4 * 2,
            verticesBuffer
        )
        GLES20.glVertexAttribPointer(
            texturePositionHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            4 * 2,
            textureBuffer
        )
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(texturePositionHandle)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES20.glUniform1i(textureHandle, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glFlush()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    @SuppressLint("Recycle")
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        initTextures()
        surfaceTexture = SurfaceTexture(textures[0])
        surfaceTexture?.setOnFrameAvailableListener(this)

        GLES20.glClearColor(0f, 0f, 0f, 1f)
        initializeProgram()

        onSurfaceReady?.invoke()
        GLInited = true
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        synchronized(this) {
            updateSurfaceTexture = true
            requestRender()
        }
    }

    @SuppressLint("Recycle")
    fun getSurface(size: Size): Surface {
        val surface = this.surface
        if (surface != null) {
            return surface
        }

        this.size = size
        surfaceTexture?.setDefaultBufferSize(size.width, size.height)
        this.surface = Surface(surfaceTexture)
        return this.surface!!
    }

    private fun initializeProgram() {
        vertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
        GLES20.glShaderSource(vertexShader, vertexShaderCode)
        GLES20.glCompileShader(vertexShader)

        fragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
        GLES20.glShaderSource(fragmentShader, fragmentShaderCode)
        GLES20.glCompileShader(fragmentShader)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)

        GLES20.glLinkProgram(program)
    }

    private fun initTextures() {
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])

        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_NEAREST
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
    }

    companion object {
        private const val vertexShaderCode = "" +
                "attribute vec2 vPosition;\n" +
                "attribute vec2 vTexCoord;\n" +
                "varying vec2 texCoord;\n" +
                "void main() {\n" +
                "  texCoord = vTexCoord;\n" +
                "  gl_Position = vec4 ( vPosition.x, vPosition.y, 0.0, 1.0 );\n" +
                "}"

        private const val fragmentShaderCode = "" +
                "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float;\n" +
                "uniform samplerExternalOES sTexture;\n" +
                "varying vec2 texCoord;\n" +
                "void main() {\n" +
                "  gl_FragColor = texture2D(sTexture,texCoord);\n" +
                "}"

        private val LOG_TAG = CameraPreviewView::class.java.simpleName
    }
}