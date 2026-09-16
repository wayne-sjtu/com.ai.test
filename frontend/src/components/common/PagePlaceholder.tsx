import { Card, Empty, Typography } from 'antd'

const { Paragraph, Title } = Typography

/** 页面骨架占位：标注页面用途与对接的后端接口，逐页填充时替换 */
export function PagePlaceholder({ title, description, endpoints }: {
  title: string
  description: string
  endpoints?: string[]
}) {
  return (
    <Card>
      <Title level={4}>{title}</Title>
      <Paragraph type="secondary">{description}</Paragraph>
      {endpoints && (
        <ul style={{ paddingLeft: 20, color: '#888' }}>
          {endpoints.map((e) => (
            <li key={e}>
              <code>{e}</code>
            </li>
          ))}
        </ul>
      )}
      <Empty description="页面填充中" />
    </Card>
  )
}
