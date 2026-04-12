import SwiftUI
import MapKit
import FreeWheelCore

/// MapKit-based route view for trip detail. Shows a speed-colored polyline with
/// start/end markers and a movable selection dot linked to the chart scrubber.
struct RideMapView: UIViewRepresentable {
    let routePoints: [RoutePoint]
    /// Currently selected route point (driven by chart scrubber or map tap).
    let selectedPoint: RoutePoint?
    /// Called when the user taps on/near the route (point) or taps away (nil).
    let onTapPoint: ((RoutePoint?) -> Void)?

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.delegate = context.coordinator
        mapView.isRotateEnabled = false
        mapView.showsCompass = false

        let tap = UITapGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.handleTap(_:))
        )
        mapView.addGestureRecognizer(tap)

        return mapView
    }

    func updateUIView(_ mapView: MKMapView, context: Context) {
        let coordinator = context.coordinator

        // Rebuild polyline only when route data changes
        if coordinator.lastRouteCount != routePoints.count {
            coordinator.lastRouteCount = routePoints.count
            rebuildRoute(on: mapView, coordinator: coordinator)
        }

        updateSelectedMarker(on: mapView, coordinator: coordinator)
    }

    // MARK: - Route Building

    private func rebuildRoute(on mapView: MKMapView, coordinator: Coordinator) {
        mapView.removeOverlays(mapView.overlays)
        mapView.removeAnnotations(mapView.annotations)
        coordinator.selectedAnnotation = nil

        guard routePoints.count >= 2 else { return }

        let coords = routePoints.map {
            CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude)
        }

        // Polyline + gradient colors
        let polyline = MKPolyline(coordinates: coords, count: coords.count)
        let (colors, locations) = buildGradient(coords: coords)
        coordinator.polylineColors = colors
        coordinator.polylineLocations = locations
        mapView.addOverlay(polyline)

        // Start / end markers
        let startAnn = RouteEndAnnotation(coordinate: coords.first!, isStart: true)
        let endAnn = RouteEndAnnotation(coordinate: coords.last!, isStart: false)
        mapView.addAnnotations([startAnn, endAnn])

        // Frame the route with padding
        let rect = polyline.boundingMapRect
        let insets = UIEdgeInsets(top: 30, left: 30, bottom: 30, right: 30)
        mapView.setVisibleMapRect(rect, edgePadding: insets, animated: false)
    }

    private func buildGradient(coords: [CLLocationCoordinate2D]) -> ([UIColor], [CGFloat]) {
        let speeds = routePoints.map { $0.speedKmh }
        let minSpeed = speeds.min() ?? 0
        let maxSpeed = speeds.max() ?? 0
        let speedRange = maxSpeed - minSpeed

        // Cumulative distances for location fractions
        var cumDist = [0.0]
        for i in 1..<coords.count {
            let prev = CLLocation(latitude: coords[i - 1].latitude, longitude: coords[i - 1].longitude)
            let curr = CLLocation(latitude: coords[i].latitude, longitude: coords[i].longitude)
            cumDist.append(cumDist[i - 1] + prev.distance(from: curr))
        }
        let totalDist = cumDist.last ?? 1

        var colors: [UIColor] = []
        var locations: [CGFloat] = []

        for i in 0..<routePoints.count {
            let fraction = speedRange > 0 ? (routePoints[i].speedKmh - minSpeed) / speedRange : 0
            colors.append(speedColor(fraction: fraction))
            locations.append(CGFloat(totalDist > 0 ? cumDist[i] / totalDist : 0))
        }

        return (colors, locations)
    }

    // MARK: - Selected Point Marker

    private func updateSelectedMarker(on mapView: MKMapView, coordinator: Coordinator) {
        if let point = selectedPoint {
            let coord = CLLocationCoordinate2D(latitude: point.latitude, longitude: point.longitude)
            if let existing = coordinator.selectedAnnotation {
                existing.coordinate = coord
            } else {
                let annotation = SelectedPointAnnotation()
                annotation.coordinate = coord
                coordinator.selectedAnnotation = annotation
                mapView.addAnnotation(annotation)
            }
        } else if let existing = coordinator.selectedAnnotation {
            mapView.removeAnnotation(existing)
            coordinator.selectedAnnotation = nil
        }
    }

    // MARK: - Color Helpers

    /// Green (0.0) -> Yellow (0.5) -> Red (1.0)
    private func speedColor(fraction: Double) -> UIColor {
        let clamped = min(max(fraction, 0), 1)
        if clamped < 0.5 {
            let t = clamped * 2
            return UIColor(red: t, green: 1.0, blue: 0.0, alpha: 1.0)
        } else {
            let t = (clamped - 0.5) * 2
            return UIColor(red: 1.0, green: 1.0 - t, blue: 0.0, alpha: 1.0)
        }
    }

    // MARK: - Annotation Types

    class SelectedPointAnnotation: MKPointAnnotation {}

    class RouteEndAnnotation: MKPointAnnotation {
        let isStart: Bool
        init(coordinate: CLLocationCoordinate2D, isStart: Bool) {
            self.isStart = isStart
            super.init()
            self.coordinate = coordinate
        }
    }

    // MARK: - Coordinator

    class Coordinator: NSObject, MKMapViewDelegate {
        var parent: RideMapView
        var lastRouteCount = 0
        var selectedAnnotation: SelectedPointAnnotation?
        var polylineColors: [UIColor] = []
        var polylineLocations: [CGFloat] = []

        init(parent: RideMapView) {
            self.parent = parent
        }

        // MARK: Overlay Rendering

        func mapView(_ mapView: MKMapView, rendererFor overlay: any MKOverlay) -> MKOverlayRenderer {
            guard let polyline = overlay as? MKPolyline else {
                return MKOverlayRenderer(overlay: overlay)
            }
            let renderer = MKGradientPolylineRenderer(polyline: polyline)
            renderer.setColors(polylineColors, locations: polylineLocations)
            renderer.lineWidth = 4
            renderer.lineCap = .round
            renderer.lineJoin = .round
            return renderer
        }

        // MARK: Annotation Views

        func mapView(_ mapView: MKMapView, viewFor annotation: any MKAnnotation) -> MKAnnotationView? {
            if let selected = annotation as? SelectedPointAnnotation {
                return selectedPointView(for: selected, on: mapView)
            }
            if let endpoint = annotation as? RouteEndAnnotation {
                return endpointView(for: endpoint, on: mapView)
            }
            return nil
        }

        private func selectedPointView(for annotation: SelectedPointAnnotation, on mapView: MKMapView) -> MKAnnotationView {
            let id = "selected"
            let view = mapView.dequeueReusableAnnotationView(withIdentifier: id)
                ?? MKAnnotationView(annotation: annotation, reuseIdentifier: id)
            view.annotation = annotation
            let size: CGFloat = 14
            let renderer = UIGraphicsImageRenderer(size: CGSize(width: size, height: size))
            view.image = renderer.image { ctx in
                UIColor.systemBlue.setFill()
                ctx.cgContext.fillEllipse(in: CGRect(x: 0, y: 0, width: size, height: size))
                UIColor.white.setStroke()
                ctx.cgContext.setLineWidth(2)
                ctx.cgContext.strokeEllipse(in: CGRect(x: 1, y: 1, width: size - 2, height: size - 2))
            }
            view.centerOffset = .zero
            view.layer.zPosition = 1
            return view
        }

        private func endpointView(for annotation: RouteEndAnnotation, on mapView: MKMapView) -> MKAnnotationView {
            let id = annotation.isStart ? "start" : "end"
            let view = mapView.dequeueReusableAnnotationView(withIdentifier: id)
                ?? MKAnnotationView(annotation: annotation, reuseIdentifier: id)
            view.annotation = annotation
            let size: CGFloat = 10
            let color: UIColor = annotation.isStart ? .systemGreen : .systemRed
            let renderer = UIGraphicsImageRenderer(size: CGSize(width: size, height: size))
            view.image = renderer.image { ctx in
                color.setFill()
                ctx.cgContext.fillEllipse(in: CGRect(x: 0, y: 0, width: size, height: size))
            }
            view.centerOffset = .zero
            return view
        }

        // MARK: Tap → Route Point

        @objc func handleTap(_ gesture: UITapGestureRecognizer) {
            guard let mapView = gesture.view as? MKMapView else { return }
            let tapPoint = gesture.location(in: mapView)
            let tapCoord = mapView.convert(tapPoint, toCoordinateFrom: mapView)
            let tapLocation = CLLocation(latitude: tapCoord.latitude, longitude: tapCoord.longitude)

            var bestPoint: RoutePoint?
            var bestDistance = Double.infinity

            for point in parent.routePoints {
                let loc = CLLocation(latitude: point.latitude, longitude: point.longitude)
                let d = tapLocation.distance(from: loc)
                if d < bestDistance {
                    bestDistance = d
                    bestPoint = point
                }
            }

            // Scale tap threshold with zoom: 44pt converted to meters
            let threshold = tapThresholdMeters(mapView: mapView)
            if bestDistance <= threshold, let point = bestPoint {
                parent.onTapPoint?(point)
            } else {
                parent.onTapPoint?(nil)
            }
        }

        private func tapThresholdMeters(mapView: MKMapView) -> Double {
            let center = mapView.centerCoordinate
            let centerPt = mapView.convert(center, toPointTo: mapView)
            let offsetPt = CGPoint(x: centerPt.x + 44, y: centerPt.y)
            let offsetCoord = mapView.convert(offsetPt, toCoordinateFrom: mapView)
            return CLLocation(latitude: center.latitude, longitude: center.longitude)
                .distance(from: CLLocation(latitude: offsetCoord.latitude, longitude: offsetCoord.longitude))
        }
    }
}
