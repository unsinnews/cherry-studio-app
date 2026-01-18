import { Switch } from 'heroui-native'
import React from 'react'
import { useTranslation } from 'react-i18next'
import { Alert, Platform } from 'react-native'

import {
  Container,
  Group,
  GroupTitle,
  HeaderBar,
  SafeAreaContainer,
  Text,
  XStack,
  YStack
} from '@/componentsV2'
import { usePreference } from '@/hooks/usePreference'
import { useFloatingWindow } from '@/hooks/useFloatingWindow'

export default function FloatingWindowSettingsScreen() {
  const { t } = useTranslation()
  const [enabled, setEnabled] = usePreference('floatingwindow.enabled')
  const [buttonSize, setButtonSize] = usePreference('floatingwindow.button_size')
  const [autoCloseResult, setAutoCloseResult] = usePreference('floatingwindow.auto_close_result')

  const {
    hasPermission,
    isServiceRunning,
    requestPermission,
    startService,
    stopService
  } = useFloatingWindow()

  const handleToggleEnabled = async (value: boolean) => {
    if (value) {
      // Check and request overlay permission first
      if (!hasPermission) {
        Alert.alert(
          t('settings.floatingwindow.permission_required_title'),
          t('settings.floatingwindow.permission_required_message'),
          [
            { text: t('common.cancel'), style: 'cancel' },
            {
              text: t('common.confirm'),
              onPress: async () => {
                await requestPermission()
              }
            }
          ]
        )
        return
      }

      // Start the service
      try {
        await startService()
        setEnabled(true)
      } catch (error) {
        Alert.alert(
          t('common.error'),
          t('settings.floatingwindow.start_service_failed')
        )
      }
    } else {
      // Stop the service
      try {
        await stopService()
        setEnabled(false)
      } catch (error) {
        Alert.alert(
          t('common.error'),
          t('settings.floatingwindow.stop_service_failed')
        )
      }
    }
  }

  const handleButtonSizeChange = (size: 'small' | 'medium' | 'large') => {
    setButtonSize(size)
    // If service is running, we need to restart it to apply the new size
    if (isServiceRunning) {
      stopService().then(() => {
        startService()
      })
    }
  }

  // Only render on Android
  if (Platform.OS !== 'android') {
    return (
      <SafeAreaContainer className="flex-1">
        <HeaderBar title={t('settings.floatingwindow.title')} />
        <Container>
          <YStack className="flex-1 items-center justify-center">
            <Text className="text-gray-500">
              {t('settings.floatingwindow.android_only')}
            </Text>
          </YStack>
        </Container>
      </SafeAreaContainer>
    )
  }

  return (
    <SafeAreaContainer className="flex-1">
      <HeaderBar title={t('settings.floatingwindow.title')} />
      <Container>
        <YStack className="flex-1 gap-6">
          {/* Enable floating window */}
          <YStack className="gap-2">
            <GroupTitle>{t('settings.floatingwindow.enable_title')}</GroupTitle>
            <Group>
              <XStack className="items-center justify-between p-4">
                <YStack className="flex-1 pr-4">
                  <Text className="text-lg">{t('settings.floatingwindow.enable')}</Text>
                  <Text className="text-sm text-gray-500">
                    {t('settings.floatingwindow.enable_description')}
                  </Text>
                </YStack>
                <Switch
                  isSelected={enabled}
                  onSelectedChange={handleToggleEnabled}
                />
              </XStack>
            </Group>
          </YStack>

          {/* Button size */}
          <YStack className="gap-2">
            <GroupTitle>{t('settings.floatingwindow.button_size_title')}</GroupTitle>
            <Group>
              <XStack className="items-center justify-between p-4">
                <Text className="text-lg">{t('settings.floatingwindow.button_size_small')}</Text>
                <Switch
                  isSelected={buttonSize === 'small'}
                  onSelectedChange={() => handleButtonSizeChange('small')}
                />
              </XStack>
              <XStack className="items-center justify-between p-4">
                <Text className="text-lg">{t('settings.floatingwindow.button_size_medium')}</Text>
                <Switch
                  isSelected={buttonSize === 'medium'}
                  onSelectedChange={() => handleButtonSizeChange('medium')}
                />
              </XStack>
              <XStack className="items-center justify-between p-4">
                <Text className="text-lg">{t('settings.floatingwindow.button_size_large')}</Text>
                <Switch
                  isSelected={buttonSize === 'large'}
                  onSelectedChange={() => handleButtonSizeChange('large')}
                />
              </XStack>
            </Group>
          </YStack>

          {/* Auto close result */}
          <YStack className="gap-2">
            <GroupTitle>{t('settings.floatingwindow.behavior_title')}</GroupTitle>
            <Group>
              <XStack className="items-center justify-between p-4">
                <YStack className="flex-1 pr-4">
                  <Text className="text-lg">{t('settings.floatingwindow.auto_close_result')}</Text>
                  <Text className="text-sm text-gray-500">
                    {t('settings.floatingwindow.auto_close_result_description')}
                  </Text>
                </YStack>
                <Switch
                  isSelected={autoCloseResult}
                  onSelectedChange={setAutoCloseResult}
                />
              </XStack>
            </Group>
          </YStack>

          {/* Instructions */}
          <YStack className="gap-2">
            <GroupTitle>{t('settings.floatingwindow.instructions_title')}</GroupTitle>
            <Group>
              <YStack className="p-4 gap-2">
                <Text className="text-sm text-gray-600">
                  {t('settings.floatingwindow.instructions')}
                </Text>
              </YStack>
            </Group>
          </YStack>
        </YStack>
      </Container>
    </SafeAreaContainer>
  )
}
