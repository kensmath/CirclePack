function outCir = appMobius2Cir(M,inCir,inv)
%outCir = appMobius2Cir(M,inCir,inv) Apply mobius to circle
%   Input circle inCir given as 2x2 matrix, apply mobius M and get
%   output as 2x2 outCir.
%      result = G^{t}*inCir*conj(G), 
%   where G = M^{-1}*det(M). 

if nargin==3 && inv~=0
	M=inv(M);
end

G=zeros(2,2);
G(1,1)=M(2,2);
G(1,2)=-M(1,2);
G(2,1)=-M(2,1);
G(2,2)=M(1,1);
GT=conj(G'); % want regular transpose, not hermition transpose


outCir=GT*inCir*conj(G);

end

