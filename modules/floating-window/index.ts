import { NativeModulesProxy, EventEmitter, Platform } from 'expo-modules-core'

import type {
  FloatingWindowModuleInterface,
  CropCompleteEvent,
  FloatingWindowError,
  ButtonClickEvent,
  ServiceStateEvent,
  FloatingWindowConfig,
  ButtonSize
} from './src/FloatingWindow.types'

export type { CropCompleteEvent, FloatingWindowError, ButtonClickEvent, ServiceStateEvent, FloatingWindowConfig, ButtonSize }

export { FloatingWindowEvents } from './src/FloatingWindow.types'

// Check if platform is supported
const isSupported = Platform.OS === 'android'

// Get native module (only available on Android)
const NativeModule: FloatingWindowModuleInterface | null = isSupported
  ? (NativeModulesProxy.FloatingWindow as FloatingWindowModuleInterface)
  : null

// Create event emitter for native events
const emitter = isSupported && NativeModule ? new EventEmitter(NativeModule as any) : null

/**
 * Check if floating window feature is supported on current platform
 */
export function isFloatingWindowSupported(): boolean {
  return isSupported
}

/**
 * Check if the app has overlay permission (SYSTEM_ALERT_WINDOW)
 * @returns Promise<boolean> - true if permission is granted
 */
export async function hasOverlayPermission(): Promise<boolean> {
  if (!NativeModule) return false
  return NativeModule.hasOverlayPermission()
}

/**
 * Request overlay permission - opens system settings
 * User needs to manually grant the permission
 */
export async function requestOverlayPermission(): Promise<void> {
  if (!NativeModule) {
    throw new Error('Floating window is only supported on Android')
  }
  return NativeModule.requestOverlayPermission()
}

/**
 * Start the floating window service
 * @param config - Optional configuration for the floating button
 */
export async function startService(config?: FloatingWindowConfig): Promise<void> {
  if (!NativeModule) {
    throw new Error('Floating window is only supported on Android')
  }
  return NativeModule.startService(config)
}

/**
 * Stop the floating window service
 */
export async function stopService(): Promise<void> {
  if (!NativeModule) {
    throw new Error('Floating window is only supported on Android')
  }
  return NativeModule.stopService()
}

/**
 * Check if the floating window service is currently running
 */
export async function isServiceRunning(): Promise<boolean> {
  if (!NativeModule) return false
  return NativeModule.isServiceRunning()
}

/**
 * Request screen capture permission and start capture
 * This will show the system permission dialog
 */
export async function requestScreenCapture(): Promise<void> {
  if (!NativeModule) {
    throw new Error('Floating window is only supported on Android')
  }
  return NativeModule.requestScreenCapture()
}

/**
 * Show the result panel with content
 * @param content - Markdown content to display
 */
export async function showResult(content: string): Promise<void> {
  if (!NativeModule) {
    throw new Error('Floating window is only supported on Android')
  }
  return NativeModule.showResult(content)
}

/**
 * Update the result panel content (for streaming)
 * @param content - Updated markdown content
 */
export async function updateResult(content: string): Promise<void> {
  if (!NativeModule) {
    throw new Error('Floating window is only supported on Android')
  }
  return NativeModule.updateResult(content)
}

/**
 * Hide the result panel
 */
export async function hideResult(): Promise<void> {
  if (!NativeModule) {
    throw new Error('Floating window is only supported on Android')
  }
  return NativeModule.hideResult()
}

/**
 * Set loading state for result panel
 * @param loading - Whether to show loading indicator
 */
export async function setResultLoading(loading: boolean): Promise<void> {
  if (!NativeModule) {
    throw new Error('Floating window is only supported on Android')
  }
  return NativeModule.setResultLoading(loading)
}

/**
 * Save button position for persistence
 */
export async function savePosition(x: number, y: number): Promise<void> {
  if (!NativeModule) {
    throw new Error('Floating window is only supported on Android')
  }
  return NativeModule.savePosition(x, y)
}

/**
 * Get saved button position
 */
export async function getPosition(): Promise<{ x: number; y: number }> {
  if (!NativeModule) {
    return { x: -1, y: -1 }
  }
  return NativeModule.getPosition()
}

// Event subscription helpers
type EventCallback<T> = (event: T) => void

/**
 * Subscribe to crop complete events
 */
export function addCropCompleteListener(callback: EventCallback<CropCompleteEvent>): { remove: () => void } {
  if (!emitter) {
    return { remove: () => {} }
  }
  const subscription = emitter.addListener('onCropComplete', callback)
  return { remove: () => subscription.remove() }
}

/**
 * Subscribe to error events
 */
export function addErrorListener(callback: EventCallback<FloatingWindowError>): { remove: () => void } {
  if (!emitter) {
    return { remove: () => {} }
  }
  const subscription = emitter.addListener('onError', callback)
  return { remove: () => subscription.remove() }
}

/**
 * Subscribe to button click events
 */
export function addButtonClickListener(callback: EventCallback<ButtonClickEvent>): { remove: () => void } {
  if (!emitter) {
    return { remove: () => {} }
  }
  const subscription = emitter.addListener('onButtonClick', callback)
  return { remove: () => subscription.remove() }
}

/**
 * Subscribe to service state change events
 */
export function addServiceStateListener(callback: EventCallback<ServiceStateEvent>): { remove: () => void } {
  if (!emitter) {
    return { remove: () => {} }
  }
  const subscription = emitter.addListener('onServiceStateChange', callback)
  return { remove: () => subscription.remove() }
}

/**
 * Subscribe to result panel close events
 */
export function addResultCloseListener(callback: EventCallback<void>): { remove: () => void } {
  if (!emitter) {
    return { remove: () => {} }
  }
  const subscription = emitter.addListener('onResultClose', callback)
  return { remove: () => subscription.remove() }
}
