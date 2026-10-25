import test from 'node:test';
import assert from 'node:assert/strict';
import {
  calculateDistanceMeters,
  calculateBearing,
  isOutsidePlayZone,
  evaluateGeofence,
  verifyCatchCode,
  assignPlayerRoles,
  canAttemptCatch,
} from './gameRules';

test('calculateDistanceMeters accurately computes distance between two coordinates', () => {
  // Coordinates in Central Park, NY: ~111m apart along meridian
  const coordA = { latitude: 40.785091, longitude: -73.968285 };
  const coordB = { latitude: 40.786091, longitude: -73.968285 };

  const distance = calculateDistanceMeters(coordA, coordB);
  assert.ok(distance > 105 && distance < 115, `Distance was ${distance}, expected ~111m`);
});

test('calculateBearing calculates directional angle 0-360 degrees', () => {
  // Due North
  const coordA = { latitude: 40.0, longitude: -74.0 };
  const coordNorth = { latitude: 40.01, longitude: -74.0 };
  const bearingNorth = calculateBearing(coordA, coordNorth);
  assert.ok(Math.abs(bearingNorth - 0) < 1 || Math.abs(bearingNorth - 360) < 1);

  // Due East
  const coordEast = { latitude: 40.0, longitude: -73.99 };
  const bearingEast = calculateBearing(coordA, coordEast);
  assert.ok(Math.abs(bearingEast - 90) < 2);
});

test('isOutsidePlayZone detects if player is inside or outside radius', () => {
  const center = { latitude: 40.7829, longitude: -73.9654 };
  const inside = { latitude: 40.7830, longitude: -73.9654 }; // ~11m
  const outside = { latitude: 40.7920, longitude: -73.9654 }; // ~1000m

  assert.equal(isOutsidePlayZone(inside, center, 200), false);
  assert.equal(isOutsidePlayZone(outside, center, 200), true);
});

test('evaluateGeofence correctly identifies INSIDE, BOUNDARY_ALERT, and OUT_OF_BOUNDS', () => {
  const center = { latitude: 40.0, longitude: -74.0 };
  const radius = 500; // 500m

  // Safe center
  const pSafe = { latitude: 40.001, longitude: -74.0 }; // ~111m
  const evalSafe = evaluateGeofence(pSafe, center, radius, 25);
  assert.equal(evalSafe.status, 'INSIDE');

  // Close to border: e.g. ~485m (within 25m buffer of 500m)
  const pBuffer = { latitude: 40.00438, longitude: -74.0 }; // ~486m
  const evalBuffer = evaluateGeofence(pBuffer, center, radius, 25);
  assert.equal(evalBuffer.status, 'BOUNDARY_ALERT');

  // Out of bounds: e.g. ~520m
  const pOut = { latitude: 40.0047, longitude: -74.0 }; // ~522m
  const evalOut = evaluateGeofence(pOut, center, radius, 25);
  assert.equal(evalOut.status, 'OUT_OF_BOUNDS');
});

test('canAttemptCatch activates strictly when within 25 meters', () => {
  const p1 = { latitude: 40.00000, longitude: -74.00000 };
  // ~5m away
  const pClose = { latitude: 40.000045, longitude: -74.00000 };
  // ~35m away, clearly outside the 25m catch range
  const pFar = { latitude: 40.000315, longitude: -74.00000 };

  assert.equal(canAttemptCatch(p1, pClose), true);
  assert.equal(canAttemptCatch(p1, pFar), false);
});

test('verifyCatchCode checks 4-digit code exact match and whitespace resilience', () => {
  assert.equal(verifyCatchCode('4821', '4821'), true);
  assert.equal(verifyCatchCode(' 4821 ', '4821'), true);
  assert.equal(verifyCatchCode('4822', '4821'), false);
});

test('assignPlayerRoles assigns expected hider to seeker ratio', () => {
  const players = ['p1', 'p2', 'p3', 'p4'];
  const roles = assignPlayerRoles(players, 3);
  const values = Object.values(roles);
  const hiders = values.filter((r) => r === 'HIDER').length;
  const seekers = values.filter((r) => r === 'SEEKER').length;

  assert.equal(hiders, 1);
  assert.equal(seekers, 3);
});
