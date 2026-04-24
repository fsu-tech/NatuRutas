package com.example.gpxeditor.view.adapters

import android.content.Intent
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.gpxeditor.R
import com.example.gpxeditor.model.entities.Route
import com.example.gpxeditor.controller.RouteDetailActivity
import com.example.gpxeditor.view.fragments.SavedRoutesFragment

class RoutesAdapter(private val routes: List<Route>, private val listener: OnItemClickListener) : RecyclerView.Adapter<RoutesAdapter.RouteViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(route: Route)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_route, parent, false)
        return RouteViewHolder(view)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        val route = routes[position]
        holder.bind(route, listener)
    }

    override fun getItemCount(): Int = routes.size

    class RouteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnCreateContextMenuListener {
        private val routeName: TextView = itemView.findViewById(R.id.routeName)
        private val routeDate: TextView = itemView.findViewById(R.id.routeDate)
        private lateinit var currentRoute: Route
        private lateinit var currentListener: OnItemClickListener

        fun bind(route: Route, listener: OnItemClickListener) {
            // Mostrar tipoRuta junto al nombre, manejando el caso de null
            val tipoRutaText = if (!route.tipoRuta.isNullOrEmpty()) " (${route.tipoRuta})" else ""
            routeName.text = "${route.name}$tipoRutaText"

            routeDate.text = route.date
            currentRoute = route
            currentListener = listener
            itemView.setOnLongClickListener {
                itemView.showContextMenu()
                true
            }
            itemView.setOnCreateContextMenuListener(this)
        }

        //Dentro de RoutesAdapter.kt
        override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
            menu?.add(0, 1, 0, "Detalles la Ruta")?.setOnMenuItemClickListener {
                val intent = Intent(itemView.context, RouteDetailActivity::class.java)
                intent.putExtra("route_id", currentRoute.id)
                itemView.context.startActivity(intent)
                true
            }
            menu?.add(0, 2, 0, "Exportar Ruta")?.setOnMenuItemClickListener {
                //Llama a la funcion del fragment, pasandole la ruta actual.
                (currentListener as? SavedRoutesFragment)?.exportRoutesToGpx(listOf(currentRoute))
                true
            }
        }

            }
        }

