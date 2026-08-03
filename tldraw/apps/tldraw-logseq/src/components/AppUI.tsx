import { useApp } from '@tldraw/react'
import { observer } from 'mobx-react-lite'
import * as React from 'react'
import { ActionBar } from './ActionBar'
import { DevTools } from './Devtools'
import { PrimaryTools } from './PrimaryTools'
import { StatusBar } from './StatusBar'
import { LogseqContext } from '../lib/logseq-context'

export const AppUI = observer(function AppUI() {
  const app = useApp()
  const { handlers } = React.useContext(LogseqContext)

  return (
    <>
      {handlers.isDev() && <StatusBar />}
      {handlers.isDev() && <DevTools />}
      {!app.readOnly && <PrimaryTools />}
      <ActionBar />
    </>
  )
})
