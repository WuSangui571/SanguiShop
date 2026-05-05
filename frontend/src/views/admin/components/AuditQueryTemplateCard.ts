import {
  defineComponent,
  h,
  type PropType,
} from 'vue'
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
    return () => h('article', { class: 'audit-template-card' }, [
      h('div', { class: 'template-head' }, [
        h('h3', props.title),
        h('div', { class: 'template-actions' }, [
          h('button', {
            type: 'button',
            class: 'secondary mini-button',
            onClick: () => emit('copy', props.kind),
          }, props.copied ? 'Copied' : 'Copy query'),
          h('button', {
            type: 'button',
            class: 'secondary mini-button',
            disabled: props.link ? undefined : true,
            title: props.link ? props.enabledTitle : props.disabledTitle,
            onClick: () => emit('open', props.kind),
          }, `Open in ${props.platformLabel}`),
        ]),
      ]),
      h('textarea', {
        readonly: true,
        value: props.template,
      }),
    ])
  },
})
