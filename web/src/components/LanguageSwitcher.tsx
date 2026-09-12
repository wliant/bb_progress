import { useTranslation } from 'react-i18next'
import { persistLanguage } from '../i18n'

export function LanguageSwitcher() {
  const { i18n, t } = useTranslation()
  const isZh = i18n.language === 'zh-CN'

  function switchTo(lang: 'zh-CN' | 'en') {
    void i18n.changeLanguage(lang)
    persistLanguage(lang)
  }

  return (
    <div className="flex rounded-full border border-slate-300 text-sm overflow-hidden" role="group" aria-label={t('language.label')}>
      <button
        type="button"
        onClick={() => switchTo('zh-CN')}
        className={`px-3 py-1 ${isZh ? 'bg-rose-500 text-white' : 'bg-white text-slate-600'}`}
      >
        {t('language.zh')}
      </button>
      <button
        type="button"
        onClick={() => switchTo('en')}
        className={`px-3 py-1 ${!isZh ? 'bg-rose-500 text-white' : 'bg-white text-slate-600'}`}
      >
        {t('language.en')}
      </button>
    </div>
  )
}
