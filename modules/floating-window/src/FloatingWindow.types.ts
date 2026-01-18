/**
 * Floating Window Module Types
 * Android-only module for overlay floating window with screen capture and AI question solving
 */

export interface CropCompleteEvent {
  /** File path to the cropped image (file:// URI) */
  imagePath: string
  /** Width of the cropped image in pixels */
  width: number
  /** Height of the cropped image in pixels */
  height: number
}

export interface FloatingWindowError {
  /** Error code for programmatic handling */
  code: 'PERMISSION_DENIED' | 'SERVICE_ERROR' | 'CAPTURE_FAILED' | 'CROP_CANCELLED' | 'UNKNOWN'
  /** Human-readable error message */
  message: string
}

export interface ButtonClickEvent {
  /** Timestamp when button was clicked */
  timestamp: number
}

export interface ServiceStateEvent {
  /** Whether the service is currently running */
  isRunning: boolean
}

export type ButtonSize = 'small' | 'medium' | 'large'

export interface FloatingWindowConfig {
  /** Button size */
  buttonSize: ButtonSize
  /** Initial X position (-1 for default) */
  positionX: number
  /** Initial Y position (-1 for default) */
  positionY: number
}

export interface FloatingWindowModuleInterface {
  // Permission methods
  hasOverlayPermission(): Promise<boolean>
  requestOverlayPermission(): Promise<void>

  // Service control
  startService(config?: FloatingWindowConfig): Promise<void>
  stopService(): Promise<void>
  isServiceRunning(): Promise<boolean>

  // Screen capture
  requestScreenCapture(): Promise<void>

  // Result panel
  showResult(content: string): Promise<void>
  updateResult(content: string): Promise<void>
  hideResult(): Promise<void>
  setResultLoading(loading: boolean): Promise<void>

  // Position persistence
  savePosition(x: number, y: number): Promise<void>
  getPosition(): Promise<{ x: number; y: number }>
}

// Event names for native event emitter
export const FloatingWindowEvents = {
  ON_CROP_COMPLETE: 'onCropComplete',
  ON_ERROR: 'onError',
  ON_BUTTON_CLICK: 'onButtonClick',
  ON_SERVICE_STATE_CHANGE: 'onServiceStateChange',
  ON_RESULT_CLOSE: 'onResultClose'
} as const

export type FloatingWindowEventName = (typeof FloatingWindowEvents)[keyof typeof FloatingWindowEvents]
