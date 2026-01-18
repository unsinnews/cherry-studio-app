/**
 * useFloatingWindow - React Hook for managing the floating window feature
 *
 * This hook provides a React-friendly interface to the FloatingWindow native module,
 * managing overlay permissions, service lifecycle, and event handling.
 *
 * Features:
 * - Permission checking and requesting
 * - Service start/stop control
 * - Event listeners for crop completion and errors
 * - Integration with preference storage for settings
 *
 * @example Basic Usage
 * ```typescript
 * function FloatingWindowSettings() {
 *   const {
 *     hasPermission,
 *     isServiceRunning,
 *     requestPermission,
 *     startService,
 *     stopService
 *   } = useFloatingWindow()
 *
 *   return (
 *     <Switch
 *       isSelected={isServiceRunning}
 *       onSelectedChange={(enabled) => enabled ? startService() : stopService()}
 *     />
 *   )
 * }
 * ```
 */

import { useCallback, useEffect, useState } from 'react'
import { Platform } from 'react-native'

import { loggerService } from '@/services/LoggerService'
import { usePreference } from '@/hooks/usePreference'

const logger = loggerService.withContext('useFloatingWindow')

// Conditionally import the native module (only on Android)
let FloatingWindowModule: typeof import('@/modules/floating-window') | null = null

if (Platform.OS === 'android') {
  try {
    FloatingWindowModule = require('@/modules/floating-window')
  } catch (error) {
    logger.warn('FloatingWindow module not available:', error as Error)
  }
}

export interface UseFloatingWindowResult {
  /** Whether overlay permission has been granted */
  hasPermission: boolean
  /** Whether the floating window service is currently running */
  isServiceRunning: boolean
  /** Check and update permission status */
  checkPermission: () => Promise<boolean>
  /** Request overlay permission (opens system settings) */
  requestPermission: () => Promise<void>
  /** Start the floating window service */
  startService: () => Promise<void>
  /** Stop the floating window service */
  stopService: () => Promise<void>
  /** Show result panel with content */
  showResult: (content: string) => Promise<void>
  /** Update result panel content */
  updateResult: (content: string) => Promise<void>
  /** Hide result panel */
  hideResult: () => Promise<void>
  /** Set loading state on result panel */
  setResultLoading: (loading: boolean) => Promise<void>
}

/**
 * React Hook for managing the floating window feature
 *
 * @returns Object with permission status, service state, and control functions
 */
export function useFloatingWindow(): UseFloatingWindowResult {
  const [hasPermission, setHasPermission] = useState(false)
  const [isServiceRunning, setIsServiceRunning] = useState(false)

  const [buttonSize] = usePreference('floatingwindow.button_size')
  const [positionX] = usePreference('floatingwindow.position_x')
  const [positionY] = usePreference('floatingwindow.position_y')

  // Check permission on mount and when app becomes active
  const checkPermission = useCallback(async () => {
    if (!FloatingWindowModule) {
      return false
    }

    try {
      const result = await FloatingWindowModule.hasOverlayPermission()
      setHasPermission(result)
      return result
    } catch (error) {
      logger.error('Failed to check overlay permission:', error as Error)
      return false
    }
  }, [])

  // Check service status
  const checkServiceStatus = useCallback(async () => {
    if (!FloatingWindowModule) {
      return false
    }

    try {
      const result = await FloatingWindowModule.isServiceRunning()
      setIsServiceRunning(result)
      return result
    } catch (error) {
      logger.error('Failed to check service status:', error as Error)
      return false
    }
  }, [])

  // Request overlay permission
  const requestPermission = useCallback(async () => {
    if (!FloatingWindowModule) {
      throw new Error('FloatingWindow module not available')
    }

    try {
      await FloatingWindowModule.requestOverlayPermission()
      // Permission status will be updated when user returns to the app
    } catch (error) {
      logger.error('Failed to request overlay permission:', error as Error)
      throw error
    }
  }, [])

  // Start the floating window service
  const startService = useCallback(async () => {
    if (!FloatingWindowModule) {
      throw new Error('FloatingWindow module not available')
    }

    if (!hasPermission) {
      throw new Error('Overlay permission not granted')
    }

    try {
      await FloatingWindowModule.startService({
        buttonSize,
        positionX,
        positionY
      })
      setIsServiceRunning(true)
    } catch (error) {
      logger.error('Failed to start floating window service:', error as Error)
      throw error
    }
  }, [hasPermission, buttonSize, positionX, positionY])

  // Stop the floating window service
  const stopService = useCallback(async () => {
    if (!FloatingWindowModule) {
      throw new Error('FloatingWindow module not available')
    }

    try {
      await FloatingWindowModule.stopService()
      setIsServiceRunning(false)
    } catch (error) {
      logger.error('Failed to stop floating window service:', error as Error)
      throw error
    }
  }, [])

  // Show result panel
  const showResult = useCallback(async (content: string) => {
    if (!FloatingWindowModule) {
      throw new Error('FloatingWindow module not available')
    }

    try {
      await FloatingWindowModule.showResult(content)
    } catch (error) {
      logger.error('Failed to show result panel:', error as Error)
      throw error
    }
  }, [])

  // Update result panel
  const updateResult = useCallback(async (content: string) => {
    if (!FloatingWindowModule) {
      throw new Error('FloatingWindow module not available')
    }

    try {
      await FloatingWindowModule.updateResult(content)
    } catch (error) {
      logger.error('Failed to update result panel:', error as Error)
      throw error
    }
  }, [])

  // Hide result panel
  const hideResult = useCallback(async () => {
    if (!FloatingWindowModule) {
      throw new Error('FloatingWindow module not available')
    }

    try {
      await FloatingWindowModule.hideResult()
    } catch (error) {
      logger.error('Failed to hide result panel:', error as Error)
      throw error
    }
  }, [])

  // Set result loading state
  const setResultLoading = useCallback(async (loading: boolean) => {
    if (!FloatingWindowModule) {
      throw new Error('FloatingWindow module not available')
    }

    try {
      await FloatingWindowModule.setResultLoading(loading)
    } catch (error) {
      logger.error('Failed to set result loading:', error as Error)
      throw error
    }
  }, [])

  // Initialize on mount
  useEffect(() => {
    if (Platform.OS !== 'android') {
      return
    }

    checkPermission()
    checkServiceStatus()

    // Subscribe to service state changes
    let unsubscribe: (() => void) | undefined

    if (FloatingWindowModule) {
      unsubscribe = FloatingWindowModule.addServiceStateListener((event) => {
        setIsServiceRunning(event.isRunning)
      })
    }

    return () => {
      unsubscribe?.()
    }
  }, [checkPermission, checkServiceStatus])

  return {
    hasPermission,
    isServiceRunning,
    checkPermission,
    requestPermission,
    startService,
    stopService,
    showResult,
    updateResult,
    hideResult,
    setResultLoading
  }
}
