import PageHeader from '../ui/PageHeader'
import Card from '../ui/Card'

interface FeaturePlaceholderProps {
  title: string
  description: string
  story: string
}

export default function FeaturePlaceholder({
  title,
  description,
  story,
}: FeaturePlaceholderProps) {
  return (
    <div>
      <PageHeader
        title={title}
        description={description}
      />

      <Card className="p-6">
        <div className="text-sm font-medium text-blue-600">
          {story}
        </div>

        <p className="mt-2 text-sm text-slate-600">
          Khung giao diện đã sẵn sàng.
          Thành viên phụ trách sẽ triển khai chức năng tại module này.
        </p>
      </Card>
    </div>
  )
}