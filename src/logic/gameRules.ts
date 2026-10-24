/**
 * Pure logic calculations for Manhunt:
 * - Haversine distance in meters
 * - Initial bearing (degrees 0-360)
 * - Geofence out-of-zone check
 * - Role assignment ratio (1 Hider per N Seekers)
 * - Catch code verification
 */

export interface LatLng {
  latitude: number;
  longitude: number;
}

/**
 * Calculates Haversine distance in meters between two coordinates.
 */
export function calculateDistanceMeters(coord1: LatLng, coord2: LatLng): number {
  const R = 6371000; // Earth radius in meters
  const dLat = ((coord2.latitude - coord1.latitude) * Math.PI) / 180;
  const dLon = ((coord2.longitude - coord1.longitude) * Math.PI) / 180;
  const lat1 = (coord1.latitude * Math.PI) / 180;
  const lat2 = (coord2.latitude * Math.PI) / 180;

  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(lat1) * Math.cos(lat2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return Math.round(R * c * 10) / 10;
}

/**
 * Calculates initial bearing from coord1 to coord2 in degrees (0 - 360).
 */
export function calculateBearing(coord1: LatLng, coord2: LatLng): number {
  const lat1 = (coord1.latitude * Math.PI) / 180;
  const lat2 = (coord2.latitude * Math.PI) / 180;
  const dLon = ((coord2.longitude - coord1.longitude) * Math.PI) / 180;

  const y = Math.sin(dLon) * Math.cos(lat2);
  const x =
    Math.cos(lat1) * Math.sin(lat2) -
    Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);

  let brng = (Math.atan2(y, x) * 180) / Math.PI;
  return (brng + 360) % 360;
}

/**
 * Checks if a player's coordinate is outside the circular play zone.
 */
export function isOutsidePlayZone(
  playerCoord: LatLng,
  zoneCenter: LatLng,
  zoneRadiusMeters: number
): boolean {
  const dist = calculateDistanceMeters(playerCoord, zoneCenter);
  return dist > zoneRadiusMeters;
}

export type GeofenceStatus = 'INSIDE' | 'BOUNDARY_ALERT' | 'OUT_OF_BOUNDS';

/**
 * High-accuracy Geofence evaluation with boundary buffer detection.
 */
export function evaluateGeofence(
  playerCoord: LatLng,
  zoneCenter: LatLng,
  zoneRadiusMeters: number,
  bufferMeters: number = 25
): {
  status: GeofenceStatus;
  distanceFromCenter: number;
  distanceToBoundary: number;
} {
  const dist = calculateDistanceMeters(playerCoord, zoneCenter);
  const distToEdge = zoneRadiusMeters - dist;

  if (dist > zoneRadiusMeters) {
    return {
      status: 'OUT_OF_BOUNDS',
      distanceFromCenter: dist,
      distanceToBoundary: dist - zoneRadiusMeters,
    };
  } else if (distToEdge <= bufferMeters) {
    return {
      status: 'BOUNDARY_ALERT',
      distanceFromCenter: dist,
      distanceToBoundary: distToEdge,
    };
  } else {
    return {
      status: 'INSIDE',
      distanceFromCenter: dist,
      distanceToBoundary: distToEdge,
    };
  }
}

/**
 * Generates a 4-digit PIN for catch confirmation.
 */
export function generateCatchCode(): string {
  return Math.floor(1000 + Math.random() * 9000).toString();
}

/**
 * Verifies if entered catch code matches target hider's catch code.
 */
export function verifyCatchCode(enteredCode: string, actualCode: string): boolean {
  return enteredCode.trim() === actualCode.trim();
}

/**
 * Assigns roles according to target ratio (1 Hider per N Seekers).
 * At least 1 Hider is always assigned if total players >= 2.
 */
export function assignPlayerRoles(
  playerIds: string[],
  hiderRatio: number = 3
): Record<string, 'SEEKER' | 'HIDER'> {
  if (playerIds.length === 0) return {};
  if (playerIds.length === 1) return { [playerIds[0]]: 'HIDER' };

  const hiderCount = Math.max(1, Math.floor(playerIds.length / (hiderRatio + 1)) || 1);
  const shuffled = [...playerIds].sort(() => 0.5 - Math.random());

  const result: Record<string, 'SEEKER' | 'HIDER'> = {};
  shuffled.forEach((id, index) => {
    result[id] = index < hiderCount ? 'HIDER' : 'SEEKER';
  });

  return result;
}

/**
 * Determines if Seeker is close enough (< 10m) to catch a Hider.
 */
export function canAttemptCatch(seekerCoord: LatLng, hiderCoord: LatLng, maxDistMeters = 10): boolean {
  const dist = calculateDistanceMeters(seekerCoord, hiderCoord);
  return dist <= maxDistMeters;
}
