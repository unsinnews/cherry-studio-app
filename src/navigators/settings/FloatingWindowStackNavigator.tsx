import { createStackNavigator, TransitionPresets } from '@react-navigation/stack'
import React from 'react'

import FloatingWindowSettingsScreen from '@/screens/settings/floatingwindow/FloatingWindowSettingsScreen'

export type FloatingWindowStackParamList = {
  FloatingWindowSettingsScreen: undefined
}

const Stack = createStackNavigator<FloatingWindowStackParamList>()

export default function FloatingWindowStackNavigator() {
  return (
    <Stack.Navigator
      screenOptions={{
        headerShown: false,
        gestureResponseDistance: 9999,
        ...TransitionPresets.SlideFromRightIOS
      }}>
      <Stack.Screen name="FloatingWindowSettingsScreen" component={FloatingWindowSettingsScreen} />
    </Stack.Navigator>
  )
}
