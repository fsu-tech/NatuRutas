package com.example.gpxeditor.view.customviews
import android.graphics.*
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

/** Route line with chevrons pointing in the order of its GPS points. */
class DirectionalRouteOverlay : Overlay() {
    val outlinePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLUE; style=Paint.Style.STROKE; strokeWidth=10f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
    var width: Float
        get() = outlinePaint.strokeWidth
        set(value) { outlinePaint.strokeWidth = value }
    private val points=mutableListOf<GeoPoint>(); private val line=Path(); private val arrow=Path()
    private val arrowPaint=arrowPaint(Color.WHITE); private val shadowPaint=arrowPaint(Color.argb(185,0,0,0))
    fun setPoints(value:List<GeoPoint>?){ points.clear(); if(value!=null) points.addAll(value) }
    override fun draw(canvas:Canvas,mapView:MapView,shadow:Boolean){
        if(shadow||points.size<2)return
        val density=mapView.resources.displayMetrics.density
        val screen=points.map{mapView.projection.toPixels(it,Point())}
        line.reset(); line.moveTo(screen[0].x.toFloat(),screen[0].y.toFloat()); screen.drop(1).forEach{line.lineTo(it.x.toFloat(),it.y.toFloat())}; canvas.drawPath(line,outlinePaint)
        val spacing=72f*density; val size=7f*density; arrowPaint.strokeWidth=1.8f*density; shadowPaint.strokeWidth=arrowPaint.strokeWidth+2f*density
        var next=spacing*.65f
        for(i in 1 until screen.size){ val a=screen[i-1]; val b=screen[i]; val dx=(b.x-a.x).toFloat(); val dy=(b.y-a.y).toFloat(); val length=hypot(dx,dy); if(length<1f)continue; val ux=dx/length; val uy=dy/length; var at=next
            while(at<=length){chevron(canvas,a.x+ux*at,a.y+uy*at,ux,uy,size);at+=spacing}; next=at-length }
    }
    private fun chevron(c:Canvas,x:Float,y:Float,ux:Float,uy:Float,s:Float){ val tx=x+ux*s*.55f;val ty=y+uy*s*.55f;val rx=x-ux*s*.55f;val ry=y-uy*s*.55f;val sx=-uy*s*.55f;val sy=ux*s*.55f;arrow.reset();arrow.moveTo(rx+sx,ry+sy);arrow.lineTo(tx,ty);arrow.lineTo(rx-sx,ry-sy);c.drawPath(arrow,shadowPaint);c.drawPath(arrow,arrowPaint) }
    private fun arrowPaint(c:Int)=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=c;style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
}
