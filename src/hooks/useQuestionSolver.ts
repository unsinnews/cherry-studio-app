/**
 * useQuestionSolver - React Hook for solving questions from screenshots
 *
 * This hook integrates the floating window feature with AI question solving,
 * handling the flow from image capture to AI response display.
 *
 * Features:
 * - Listen for crop completion events
 * - Send cropped images to the question-solver assistant
 * - Stream AI responses to the floating window result panel
 *
 * @example Basic Usage
 * ```typescript
 * function QuestionSolverController() {
 *   const { isProcessing, error, solveQuestion } = useQuestionSolver()
 *
 *   // Manually trigger solving for a specific image
 *   const handleSolve = async () => {
 *     await solveQuestion('file:///path/to/image.png')
 *   }
 *
 *   return isProcessing ? <Text>Processing...</Text> : null
 * }
 * ```
 */

import { useCallback, useEffect, useRef, useState } from 'react'
import { Platform } from 'react-native'
import { v4 as uuid } from 'uuid'

import { fetchChatCompletion } from '@/services/ApiService'
import { assistantService } from '@/services/AssistantService'
import { loggerService } from '@/services/LoggerService'
import { ChunkType } from '@/types/chunk'
import type { FileMetadata } from '@/types/file'
import { FileTypes } from '@/types/file'
import type { Message } from '@/types/message'
import { useFloatingWindow } from './useFloatingWindow'

const logger = loggerService.withContext('useQuestionSolver')

// Conditionally import the native module (only on Android)
let FloatingWindowModule: typeof import('@/modules/floating-window') | null = null

if (Platform.OS === 'android') {
  try {
    FloatingWindowModule = require('@/modules/floating-window')
  } catch (error) {
    logger.warn('FloatingWindow module not available:', error as Error)
  }
}

export interface UseQuestionSolverResult {
  /** Whether a question is currently being processed */
  isProcessing: boolean
  /** Current AI response content */
  responseContent: string
  /** Last error that occurred */
  error: string | null
  /** Manually solve a question from an image path */
  solveQuestion: (imagePath: string) => Promise<void>
}

/**
 * React Hook for solving questions from screenshots
 *
 * Automatically listens for crop completion events and processes
 * the cropped image through the AI question-solver assistant.
 *
 * @returns Object with processing state and manual solve function
 */
export function useQuestionSolver(): UseQuestionSolverResult {
  const [isProcessing, setIsProcessing] = useState(false)
  const [responseContent, setResponseContent] = useState('')
  const [error, setError] = useState<string | null>(null)

  const { showResult, updateResult, setResultLoading, hideResult } = useFloatingWindow()
  const abortControllerRef = useRef<AbortController | null>(null)

  /**
   * Process a cropped image and get AI response
   */
  const solveQuestion = useCallback(
    async (imagePath: string) => {
      if (isProcessing) {
        logger.warn('Already processing a question')
        return
      }

      setIsProcessing(true)
      setError(null)
      setResponseContent('')

      // Show loading state in result panel
      try {
        await showResult('')
        await setResultLoading(true)
      } catch (e) {
        logger.error('Failed to show result panel:', e as Error)
      }

      try {
        // Get the question-solver assistant
        const assistant = await assistantService.getAssistant('question-solver')
        if (!assistant) {
          throw new Error('Question solver assistant not found')
        }

        // Create file metadata for the image
        const imageFile: FileMetadata = {
          id: uuid(),
          name: `screenshot_${Date.now()}.png`,
          origin_name: 'screenshot.png',
          path: imagePath,
          size: 0,
          ext: '.png',
          type: FileTypes.IMAGE,
          created_at: Date.now(),
          count: 1
        }

        // Create the message with the image
        const userMessage: Message = {
          id: uuid(),
          role: 'user',
          content: '请解答图片中的题目',
          assistantId: assistant.id,
          topicId: '',
          createdAt: new Date().toISOString(),
          type: 'text',
          status: 'success',
          files: [imageFile]
        }

        // Create abort controller for cancellation
        abortControllerRef.current = new AbortController()

        let accumulatedContent = ''

        // Call the AI service
        await fetchChatCompletion({
          messages: [userMessage],
          assistant,
          options: {
            signal: abortControllerRef.current.signal
          },
          onChunkReceived: async (chunk) => {
            switch (chunk.type) {
              case ChunkType.THINKING:
              case ChunkType.TEXT:
                if (chunk.text) {
                  accumulatedContent += chunk.text
                  setResponseContent(accumulatedContent)
                  try {
                    await updateResult(accumulatedContent)
                  } catch (e) {
                    // Ignore update errors during streaming
                  }
                }
                break

              case ChunkType.LLM_RESPONSE_CREATED:
                // Response started, hide loading
                try {
                  await setResultLoading(false)
                } catch (e) {
                  // Ignore error
                }
                break

              case ChunkType.ERROR:
                setError(chunk.error?.message || 'Unknown error')
                logger.error('AI response error:', chunk.error)
                break

              case ChunkType.LLM_RESPONSE_FINISHED:
                // Response complete
                logger.info('Question solving completed')
                break
            }
          }
        })

        setIsProcessing(false)
      } catch (e) {
        const errorMessage = (e as Error).message || 'Failed to solve question'
        setError(errorMessage)
        setIsProcessing(false)

        logger.error('Failed to solve question:', e as Error)

        try {
          await setResultLoading(false)
          await updateResult(`错误: ${errorMessage}`)
        } catch (updateError) {
          // Ignore update errors
        }
      }
    },
    [isProcessing, showResult, updateResult, setResultLoading]
  )

  // Listen for crop completion events
  useEffect(() => {
    if (Platform.OS !== 'android' || !FloatingWindowModule) {
      return
    }

    const subscription = FloatingWindowModule.addCropCompleteListener((event) => {
      logger.info('Crop completed:', event)
      solveQuestion(event.imagePath)
    })

    return () => {
      subscription()
    }
  }, [solveQuestion])

  // Listen for errors from the native module
  useEffect(() => {
    if (Platform.OS !== 'android' || !FloatingWindowModule) {
      return
    }

    const subscription = FloatingWindowModule.addErrorListener((event) => {
      logger.error('FloatingWindow error:', event.code, event.message)
      setError(event.message)
    })

    return () => {
      subscription()
    }
  }, [])

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      abortControllerRef.current?.abort()
    }
  }, [])

  return {
    isProcessing,
    responseContent,
    error,
    solveQuestion
  }
}
