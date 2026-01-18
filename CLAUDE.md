# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Initial Setup

```bash
pnpm install
npx drizzle-kit generate           # Generate database migrations (required)
cd packages/react-native-streamable-http && npm install && npm run build
```

### Running the App

```bash
pnpm start                         # Start Expo development server
npx expo prebuild -p ios/android   # Generate native code (first time only)
pnpm ios                           # Run on iOS (requires Xcode)
pnpm android                       # Run on Android (requires Android SDK)
```

Note: Use physical devices or simulators, not Expo Go.

### Code Quality

- `pnpm lint` - Run ESLint with auto-fix
- `pnpm format` - Run Prettier formatting
- `pnpm check` - Run typecheck, i18n validation, lint, and format
- `pnpm test` - Run Jest tests with watch mode
- `pnpm test:ci` - Run tests in CI mode with coverage
- `pnpm check:i18n` - Validate translations for missing keys
- `pnpm sync:i18n` - Sync translation keys across all language files

### Database

- `npx drizzle-kit generate` - Generate migrations after schema changes
- `npx drizzle-kit studio` - Open Drizzle Studio for database inspection

### Building

| Target | Command |
|--------|---------|
| Android debug (local) | `cd android && ./gradlew assembleDebug` |
| Android release (local) | `cd android && ./gradlew assembleRelease` |
| iOS debug (local) | `cd ios && xcodebuild -scheme CherryStudio -configuration Debug` |
| iOS release (local) | `cd ios && xcodebuild -scheme CherryStudio -configuration Release` |
| EAS Android | `eas build --platform android` |
| EAS iOS | `eas build --platform ios` |

**Windows long path issue**: Due to node_modules path length, Windows builds may fail. Solutions: use a short project path (e.g., `C:\proj`), use WSL, or upgrade Ninja to 1.12.1 in Android SDK.

## Architecture Overview

Cherry Studio is a React Native/Expo AI chat application with a service-based architecture using optimistic updates and multi-layer caching.

### Data Layer

- **SQLite + Drizzle ORM**: Primary storage for entities (assistants, topics, messages, providers)
- **PreferenceService**: SQLite-backed user preferences with lazy loading and optimistic updates
- **Service Layer Caching**: Each service (Topic, Assistant, Provider, MCP) uses LRU caches with TTL

### AI Core Layer (`src/aiCore/`)

Provider abstraction supporting multiple LLM services (OpenAI, Anthropic, Google, Gemini, etc.):

- `Provider` classes handle authentication and request formatting
- `StreamingService` handles Server-Sent Events for real-time responses
- Middleware builder pattern for request/response processing (see `src/aiCore/middleware/`)
- AI SDK integration with `@ai-sdk/*` packages

### Service Layer (`src/services/`)

Each service follows a singleton pattern with optimistic updates and subscription-based reactivity:

- **TopicService**: Manages conversations with current topic + LRU(5) + TTL caching
- **AssistantService**: System assistants (permanent cache) + LRU(10) + TTL caching
- **ProviderService**: Default provider (permanent) + LRU(10) + TTL caching
- **McpService**: MCP servers with LRU(20) + TTL caching
- **WebSearchProviderService**: Search providers with LRU(5) + TTL caching
- **PreferenceService**: User settings with lazy loading and optimistic updates
- **BackupService**: Data import/export functionality
- **LoggerService**: Application logging with context
- **FileService**: File upload/management operations

### Navigation Structure

Four-layer hierarchical navigation with React Navigation v7:

1. **MainStackNavigator**: Root (WelcomeScreen → HomeScreen)
2. **AppDrawerNavigator**: Main drawer with Home, Assistant, Settings modules
3. **Feature Navigators**: HomeStackNavigator, AssistantStackNavigator, SettingsStackNavigator
4. **Settings Sub-Navigators**: GeneralSettings, Providers, DataSources, WebSearch, About

See `docs/navigation.md` for complete navigation architecture.

## Key Patterns

### Service Pattern with Caching

All data services use a consistent pattern:
- Singleton instance exported from service file
- Multi-layer caching (permanent + LRU + TTL)
- Optimistic updates with automatic rollback on failure
- Subscription system for React components via `useSyncExternalStore`
- Request queuing for concurrent update serialization

```typescript
// React component usage
const { topic, isLoading, updateTopic } = useTopic(topicId)
await updateTopic({ name: 'New Name' })  // Optimistic update

// Non-React usage
import { topicService } from '@/services/TopicService'
const topic = await topicService.getTopic(topicId)
```

### Compound Components

Complex UI components use the compound component pattern (see `src/componentsV2/features/ChatScreen/MessageInput/`):

```tsx
<MessageInput topic={topic} assistant={assistant}>
  <MessageInput.Main>
    <MessageInput.ToolButton />
    <MessageInput.InputArea>
      <MessageInput.TextField />
      <MessageInput.Actions />
    </MessageInput.InputArea>
  </MessageInput.Main>
  <MessageInput.AccessoryBar />
</MessageInput>
```

### Internationalization

Supports 5 languages with strict validation. Use `t('key')` from react-i18next. Translation files in `src/i18n/locales/`.

## Development Notes

### Data Documentation

**IMPORTANT**: When working with data operations, consult `docs/data-zh.md` for comprehensive documentation:
- Complete service architecture with caching strategies
- Full SQLite database schema with all tables and indexes
- Data flow patterns and entity relationships
- Storage considerations and persistence rules

### Database Migrations

Always run `npx drizzle-kit generate` after schema changes. The app uses Metro bundler configuration supporting `.sql` file imports.

### AI Provider Integration

When adding new LLM providers:
1. Use appropriate `@ai-sdk/*` package or create custom provider
2. Add provider configuration to `src/config/providers/`
3. Use middleware builder for provider-specific transformations

### React Compiler

This project uses React Compiler (babel-plugin-react-compiler) for automatic memoization. Avoid manual `useCallback` and `useMemo` unless expressing explicit intent.

### Testing

Jest with Expo preset. Place tests adjacent to source files with `.test.ts` suffix or in `__tests__/` directories.

### Code Style

- ESLint enforces import sorting and unused import removal
- Prettier handles formatting
- Run `pnpm check` before committing

## Logging

```typescript
import { loggerService } from '@/services/LoggerService'
const logger = loggerService.withContext('moduleName')

logger.info('message', CONTEXT)
logger.error('message', new Error('error'), CONTEXT)
```

Log levels (highest to lowest): `error`, `warn`, `info`, `verbose`, `debug`, `silly`
