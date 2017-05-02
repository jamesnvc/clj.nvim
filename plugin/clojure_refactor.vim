if exists('g:loaded_clj_nvim') || !has('nvim')
  finish
endif
let g:loaded_clj_nvim = 1

function! s:set_up_commands() abort
  command! -buffer -bar -nargs=0 StartJob call cljnvim#refactoring#StartIfNotRunning()
  command! -buffer -bar -nargs=0 StopJob call cljnvim#refactoring#StopIfRunning()
  command! -buffer -bar -nargs=0 TidyNS call cljnvim#refactoring#TidyNs()
endfunction

augroup clj_nvim_connect
  autocmd!
  autocmd FileType clojure call cljnvim#refactoring#StartIfNotRunning()
  autocmd FileType clojure call s:set_up_commands()
augroup END
