import Button from '../../components/ui/Button'
import Modal from '../../components/ui/Modal'
import ReadyPickupEmailPanel from './ReadyPickupEmailPanel'

export default function PickupEmailHistoryDialog({ reservationId, onClose }:
  { reservationId: number; onClose: () => void }) {
  return <Modal label={`Lịch sử email đơn #${reservationId}`} onClose={onClose}>
    <div className="w-full min-w-0 max-w-3xl rounded-2xl bg-white p-4 shadow-xl sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg font-semibold text-slate-900">Email đơn #{reservationId}</h2>
        <Button type="button" variant="secondary" size="sm" onClick={onClose}>Đóng</Button>
      </div>
      <ReadyPickupEmailPanel key={reservationId} reservationId={reservationId} />
    </div>
  </Modal>
}
