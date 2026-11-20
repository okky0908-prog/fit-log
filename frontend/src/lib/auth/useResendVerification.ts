import { useMutation } from '@tanstack/react-query'
import { ApiError, errorMessage, resendVerificationEmail } from '../api/client'
import { useToast } from '../toast'
import { useAuth } from './authContext'

/**
 * 確認メールの再送（A-07）。結果はお知らせで伝える。
 * すでに確認済み（別の端末で済ませていた）なら、ユーザーを取り直して未確認の案内を消す
 */
export function useResendVerification() {
  const toast = useToast()
  const { reloadUser } = useAuth()
  return useMutation({
    mutationFn: resendVerificationEmail,
    onSuccess: () => toast('確認メールを送りました'),
    onError: (err) => {
      toast(errorMessage(err))
      if (err instanceof ApiError && err.code === 'ALREADY_VERIFIED') {
        void reloadUser()
      }
    },
  })
}
