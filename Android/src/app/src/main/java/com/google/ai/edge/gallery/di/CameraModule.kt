package com.google.ai.edge.gallery.di

import android.content.Context
import android.renderscript.RenderScript
import com.google.ai.edge.gallery.camera.CameraManager
import com.google.ai.edge.gallery.camera.CameraSensorIntegration
import com.google.ai.edge.gallery.camera.FrameAnalysisPipeline
import com.google.ai.edge.gallery.camera.optimization.CameraPerformanceOptimizer
import com.google.ai.edge.gallery.ml.TFLiteAnalyzer
import com.google.ai.edge.gallery.sensor.GyroscopeManager
import com.google.ai.edge.gallery.sensor.WifiManager
import com.google.ai.edge.gallery.ui.camera.CameraViewModel
import com.google.ai.edge.gallery.util.BitmapPool
import com.google.ai.edge.gallery.util.ImageProcessingUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Singleton

@Module
@InstallIn(ViewModelComponent::class)
object CameraModule {
    
    @Provides
    @ViewModelScoped
    fun provideRenderScript(@ApplicationContext context: Context): RenderScript {
        return RenderScript.create(context)
    }
    
    @Provides
    @ViewModelScoped
    fun provideBitmapPool(): BitmapPool {
        return BitmapPool.getInstance()
    }
    
    @Provides
    @ViewModelScoped
    fun provideImageProcessingUtils(
        @ApplicationContext context: Context,
        rs: RenderScript
    ) {
        ImageProcessingUtils.init(rs)
        // Cleanup will be handled by the Application class
    }
    
    @Provides
    @ViewModelScoped
    fun provideCameraPerformanceOptimizer(
        @ApplicationContext context: Context
    ): CameraPerformanceOptimizer {
        return CameraPerformanceOptimizer(
            context = context,
            targetFps = 30, // Adjust based on performance requirements
            targetAnalysisWidth = 640,
            targetAnalysisHeight = 480
        )
    }
    
    @Provides
    @ViewModelScoped
    fun provideCameraManager(
        @ApplicationContext context: Context
    ): CameraManager {
        return CameraManager(context)
    }
    
    @Provides
    @ViewModelScoped
    fun provideGyroscopeManager(
        @ApplicationContext context: Context
    ): GyroscopeManager {
        return GyroscopeManager(context)
    }
    
    @Provides
    @ViewModelScoped
    fun provideWifiManager(
        @ApplicationContext context: Context
    ): WifiManager {
        return WifiManager(context)
    }
    
    @Provides
    @ViewModelScoped
    fun provideTFLiteAnalyzer(
        @ApplicationContext context: Context
    ): TFLiteAnalyzer {
        return TFLiteAnalyzer(context)
    }
    
    @Provides
    @ViewModelScoped
    fun provideFrameAnalysisPipeline(
        tfliteAnalyzer: TFLiteAnalyzer
    ): FrameAnalysisPipeline {
        return FrameAnalysisPipeline(
            tfliteAnalyzer = tfliteAnalyzer,
            targetWidth = 300,
            targetHeight = 300,
            analysisIntervalMs = 300,
            maxResults = 5,
            minConfidence = 0.5f,
            iouThreshold = 0.5f
        )
    }
    
    @Provides
    @ViewModelScoped
    fun provideCameraSensorIntegration(
        @ApplicationContext context: Context,
        tfliteAnalyzer: TFLiteAnalyzer,
        gyroscopeManager: GyroscopeManager,
        wifiManager: WifiManager,
        performanceOptimizer: CameraPerformanceOptimizer
    ): CameraSensorIntegration {
        return CameraSensorIntegration(
            context = context,
            tfliteAnalyzer = tfliteAnalyzer,
            gyroscopeManager = gyroscopeManager,
            wifiManager = wifiManager,
            performanceOptimizer = performanceOptimizer
        )
    }
    
    @Provides
    @ViewModelScoped
    fun provideCameraViewModel(
        wifiManager: WifiManager,
        cameraSensorIntegration: CameraSensorIntegration
    ): CameraViewModel {
        return CameraViewModel(
            wifiManager = wifiManager,
            cameraSensorIntegration = cameraSensorIntegration
        )
    }
}
