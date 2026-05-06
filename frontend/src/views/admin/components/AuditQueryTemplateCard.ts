import {
  defineComponent,
  h,
  type PropType,
} from 'vue'
import { useAppPreferences } from '../../../composables/useAppPreferences'
import type { AuditQueryKind } from '../compensationDashboardModel'

export default defineComponent({
  name: 'AuditQueryTemplateCard',
  props: {
    title: {
      type: String,
      required: true,
    },
    template: {
      type: String,
      required: true,
    },
    link: {
      type: String,
      required: true,
    },
    kind: {
      type: String as PropType<AuditQueryKind>,
      required: true,
    },
    platformLabel: {
      type: String,
      required: true,
    },
    copied: {
      type: Boolean,
      required: true,
    },
    disabledTitle: {
      type: String,
      required: true,
    },
    enabledTitle: {
      type: String,
      required: true,
    },
  },
  emits: {
    copy: (_kind: AuditQueryKind) => true,
    open: (_kind: AuditQueryKind) => true,
  },
  setup(props, { emit }) {
    const { t } = useAppPreferences()

    return () => h('article', { class: 'audit-template-card' }, [
      h('div', { class: 'template-head' }, [
        h('h3', props.title),
        h('div', { class: 'template-actions' }, [
          h('button', {
            type: 'button',
            class: 'secondary mini-button',
            onClick: () => emit('copy', props.kind),
          }, props.copied ? t('dashboard.copied') : t('dashboard.copyQuery')),
          h('button', {
            type: 'button',
            class: 'secondary mini-button',
            disabled: props.link ? undefined : true,
            title: props.link ? props.enabledTitle : props.disabledTitle,
            onClick: () => emit('open', props.kind),
          }, t('dashboard.openIn', { platform: props.platformLabel })),
        ]),
      ]),
      h('textarea', {
        readonly: true,
        value: props.template,
      }),
    ])
  },
})
