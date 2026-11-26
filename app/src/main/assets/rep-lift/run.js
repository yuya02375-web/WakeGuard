(function (global) {
  'use strict';

  const EARTH_RADIUS_METERS = 6_371_000;
  const MAX_ACCURACY_METERS = 45;
  const MAX_FIX_AGE_MS = 20_000;
  const MAX_FIX_GAP_MS = 30_000;
  const MAX_SPEED_METERS_PER_SECOND = 12;

  function distanceBetween(from, to) {
    const radians = Math.PI / 180;
    const latitudeDelta = (to.latitude - from.latitude) * radians;
    const longitudeDelta = (to.longitude - from.longitude) * radians;
    const square = Math.sin(latitudeDelta / 2) ** 2
      + Math.cos(from.latitude * radians) * Math.cos(to.latitude * radians)
      * Math.sin(longitudeDelta / 2) ** 2;
    return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(Math.min(1, square)));
  }

  function readFix(position, now) {
    const { latitude, longitude, accuracy } = position.coords ?? {};
    if (!Number.isFinite(latitude) || Math.abs(latitude) > 90
      || !Number.isFinite(longitude) || Math.abs(longitude) > 180
      || !Number.isFinite(accuracy) || accuracy < 0 || accuracy > MAX_ACCURACY_METERS) {
      return null;
    }

    const timestamp = Number.isFinite(position.timestamp) ? position.timestamp : now;
    if (timestamp < now - MAX_FIX_AGE_MS || timestamp > now + 1000) return null;
    return { latitude, longitude, accuracy, timestamp };
  }

  function pace(seconds, distanceKm) {
    const result = Math.round(seconds / distanceKm);
    return seconds > 0 && result > 0 && Number.isFinite(result) ? result : null;
  }

  class RunTracker {
    constructor({
      onChange = () => {},
      geolocation = global.navigator?.geolocation,
      secureContext = global.isSecureContext !== false || typeof global.WakeGuard?.requestLocation === "function",
      document = global.document,
      now = Date.now,
    } = {}) {
      this.onChange = onChange;
      this.geolocation = secureContext ? geolocation : null;
      this.document = document;
      this.now = now;
      this.phase = 'idle';
      this.gps = 'off';
      this.source = 'manual';
      this.date = null;
      this.distanceMeters = 0;
      this.accumulatedMs = 0;
      this.startedAt = null;
      this.timer = null;
      this.lastEmittedSecond = null;
      this.watchId = null;
      this.watchGeneration = 0;
      this.watchStartedAt = null;
      this.lastFix = null;
      this.accuracyMeters = null;
      this.onVisibility = this.onVisibility.bind(this);
    }

    get canGps() {
      return typeof this.geolocation?.watchPosition === 'function';
    }

    snapshot() {
      const elapsed = this.accumulatedMs + this.currentIntervalMs();
      const seconds = Math.floor(elapsed / 1000);
      const distanceKm = Math.round(this.distanceMeters) / 1000;
      return {
        phase: this.phase,
        seconds,
        distanceKm,
        paceSeconds: pace(seconds, distanceKm),
        gps: this.gps,
        canGps: this.canGps,
        source: this.source,
        date: this.date,
        accuracyMeters: this.accuracyMeters,
        foreground: !this.document?.hidden,
      };
    }

    start({ gps = true } = {}) {
      if (this.phase === 'running' || this.phase === 'paused') return this.snapshot();
      this.clearSession();
      this.source = gps ? 'gps' : 'manual';
      this.gps = gps ? (this.canGps ? 'searching' : 'unavailable') : 'off';
      this.startedAt = this.now();
      this.date = new Date(this.startedAt).toISOString();
      this.phase = 'running';
      this.document?.addEventListener('visibilitychange', this.onVisibility);
      this.startTicker();
      this.startWatch();
      return this.emit();
    }

    pause() {
      if (this.phase !== 'running') return this.snapshot();
      this.accumulatedMs += this.currentIntervalMs();
      this.startedAt = null;
      this.phase = 'paused';
      this.stopTicker();
      this.stopWatch();
      return this.emit();
    }

    resume() {
      if (this.phase !== 'paused') return this.snapshot();
      this.startedAt = this.now();
      this.phase = 'running';
      this.startTicker();
      this.startWatch();
      return this.emit();
    }

    finish() {
      if (this.phase === 'idle') return null;
      if (this.phase === 'running') this.accumulatedMs += this.currentIntervalMs();
      this.startedAt = null;
      this.phase = 'finished';
      this.stopTicker();
      this.stopWatch();
      this.document?.removeEventListener('visibilitychange', this.onVisibility);
      const { seconds, distanceKm, paceSeconds, source, date } = this.emit();
      return { seconds, distanceKm, paceSeconds, source, date };
    }

    reset() {
      this.clearSession();
      return this.emit();
    }

    clearSession() {
      this.stopTicker();
      this.stopWatch();
      this.document?.removeEventListener('visibilitychange', this.onVisibility);
      this.phase = 'idle';
      this.gps = 'off';
      this.source = 'manual';
      this.date = null;
      this.distanceMeters = 0;
      this.accumulatedMs = 0;
      this.startedAt = null;
    }

    emit(state = this.snapshot()) {
      this.lastEmittedSecond = state.seconds;
      this.onChange(state);
      return state;
    }

    currentIntervalMs() {
      return this.startedAt === null ? 0 : Math.max(0, this.now() - this.startedAt);
    }

    startTicker() {
      this.stopTicker();
      this.timer = global.setInterval(() => {
        const state = this.snapshot();
        if (state.seconds !== this.lastEmittedSecond) this.emit(state);
      }, 250);
    }

    stopTicker() {
      if (this.timer !== null) global.clearInterval(this.timer);
      this.timer = null;
    }

    onVisibility() {
      if (this.phase !== 'running') return;
      if (this.document.hidden) {
        this.stopWatch();
        if (this.gps === 'active' || this.gps === 'weak') this.gps = 'searching';
      } else {
        this.startWatch();
      }
      this.emit();
    }

    startWatch() {
      if (this.source !== 'gps' || !this.canGps || this.document?.hidden
        || this.gps === 'denied' || this.watchId !== null) return;

      this.gps = 'searching';
      this.watchStartedAt = this.now();
      const generation = ++this.watchGeneration;
      try {
        const watchId = this.geolocation.watchPosition(
          (position) => {
            if (generation === this.watchGeneration) this.acceptPosition(position);
          },
          (error) => {
            if (generation === this.watchGeneration) this.handleGpsError(error);
          },
          { enableHighAccuracy: true, maximumAge: 0, timeout: 15_000 },
        );
        if (generation === this.watchGeneration) {
          this.watchId = watchId;
        } else {
          this.geolocation.clearWatch?.(watchId);
        }
      } catch (error) {
        this.handleGpsError(error);
      }
    }

    stopWatch() {
      this.watchGeneration += 1;
      if (this.watchId !== null) this.geolocation?.clearWatch?.(this.watchId);
      this.watchId = null;
      this.watchStartedAt = null;
      this.lastFix = null;
      this.accuracyMeters = null;
    }

    handleGpsError(error) {
      const denied = error.code === 1 || error.name === 'NotAllowedError' || error.name === 'SecurityError';
      if (denied) this.stopWatch();
      this.gps = denied ? 'denied' : error.code === 3 ? 'weak' : 'error';
      this.emit();
    }

    acceptPosition(position) {
      if (this.phase !== 'running' || this.document?.hidden) return;
      const fix = readFix(position, this.now());
      if (!fix) {
        this.gps = 'weak';
        this.accuracyMeters = null;
        this.emit();
        return;
      }
      if (fix.timestamp < this.watchStartedAt - 1000) return;

      this.gps = 'active';
      this.accuracyMeters = Math.round(fix.accuracy);
      const previous = this.lastFix;
      const elapsed = previous ? fix.timestamp - previous.timestamp : 0;
      if (!previous || elapsed >= MAX_FIX_GAP_MS) {
        this.lastFix = fix;
      } else if (elapsed > 0) {
        const distance = distanceBetween(previous, fix);
        const noiseFloor = Math.max(5, (previous.accuracy + fix.accuracy) * 0.55);
        const speed = distance / (elapsed / 1000);
        if (speed > MAX_SPEED_METERS_PER_SECOND) {
          this.gps = 'weak';
        } else if (distance >= noiseFloor) {
          this.distanceMeters += distance;
          this.lastFix = fix;
        }
      }
      this.emit();
    }

    static manual({ seconds, distanceKm } = {}) {
      if (!Number.isFinite(seconds) || seconds < 1
        || !Number.isFinite(distanceKm) || distanceKm < 0) return null;
      const wholeSeconds = Math.floor(seconds);
      const roundedKm = Math.round(distanceKm * 1000) / 1000;
      if (!Number.isFinite(roundedKm)) return null;
      return {
        seconds: wholeSeconds,
        distanceKm: roundedKm,
        paceSeconds: pace(wholeSeconds, roundedKm),
        source: 'manual',
        date: new Date().toISOString(),
      };
    }

    static formatTime(seconds) {
      if (!Number.isFinite(seconds) || seconds < 0) return '—';
      const total = Math.floor(seconds);
      const minutes = Math.floor(total / 60);
      const rest = String(total % 60).padStart(2, '0');
      if (minutes < 60) return `${String(minutes).padStart(2, '0')}:${rest}`;
      return `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, '0')}:${rest}`;
    }

    static formatPace(paceSeconds) {
      if (!(paceSeconds > 0) || !Number.isFinite(paceSeconds)) return '—';
      const total = Math.round(paceSeconds);
      return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`;
    }
  }

  global.RunTracker = RunTracker;
  if (typeof module === 'object' && module.exports) module.exports = RunTracker;
})(globalThis);
