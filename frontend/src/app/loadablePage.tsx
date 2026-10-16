import { lazy, Suspense, type ComponentProps, type ComponentType } from 'react'

/**
 * Delay loading the page implementation until its route actually renders.
 * Feature metadata and navigation remain synchronously registered.
 */
export function loadablePage<T extends ComponentType<any>>(
  importer: () => Promise<{ default: T }>,
): ComponentType<ComponentProps<T>> {
  // Create the lazy component once, outside the render function.
  const Page = lazy(importer)

  return function LoadablePage(props: ComponentProps<T>) {
    return (
      <Suspense
        fallback={
          <div className="flex min-h-32 items-center justify-center p-6 text-sm text-slate-500" role="status">
            Đang tải trang...
          </div>
        }
      >
        <Page {...props} />
      </Suspense>
    )
  }
}
