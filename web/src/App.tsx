import { Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { HomePage } from './pages/HomePage'
import { ProfilePage } from './pages/ProfilePage'
import { GrowthPage } from './pages/GrowthPage'
import { MilestonesPage } from './pages/MilestonesPage'
import { CareLogsPage } from './pages/CareLogsPage'
import { MediaPage } from './pages/MediaPage'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/growth" element={<GrowthPage />} />
        <Route path="/milestones" element={<MilestonesPage />} />
        <Route path="/care" element={<CareLogsPage />} />
        <Route path="/media" element={<MediaPage />} />
      </Route>
    </Routes>
  )
}
