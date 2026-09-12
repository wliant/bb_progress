import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import zhCN from './locales/zh-CN.json'
import en from './locales/en.json'

const STORAGE_KEY = 'bb-lang'

export function storedLanguage(): string {
  try {
    return localStorage.getItem(STORAGE_KEY) ?? 'zh-CN'
  } catch {
    return 'zh-CN'
  }
}

export function persistLanguage(lang: string) {
  try {
    localStorage.setItem(STORAGE_KEY, lang)
  } catch {
    // ignore storage failures (private mode etc.)
  }
}

i18n.use(initReactI18next).init({
  resources: {
    'zh-CN': { translation: zhCN },
    en: { translation: en },
  },
  lng: storedLanguage(),
  fallbackLng: 'zh-CN',
  interpolation: { escapeValue: false },
})

export default i18n
